package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.DocumentEventModel;
import io.github.projectopenubl.xhandler.utils.Deadlines;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Voiding (DESIGN.md §9.3, §11.3). Facturas and their notes are voided by a comunicación de baja
 * (RA), boletas and their notes by a resumen diario line with estado 3 (RC), percepciones and
 * retenciones by a resumen de reversiones (RR). GRE can only be voided in SUNAT's SOL.
 *
 * <p>Allowed only for an accepted document that the buyer doesn't have (the request attests it),
 * within 7 calendar days after its issue date (2027 rules), or after the reversal was communicated
 * for RR. Otherwise the answer is 409 and a nota de crédito is the way.
 */
@ApplicationScoped
public class VoidingService {

    private final TenantProvider tenantProvider;

    private final DocumentProvider documentProvider;

    private final DocumentEventProvider documentEventProvider;

    private final CompanyProvider companyProvider;

    /** A void request. {@code reversalCommunicatedAt} is required for percepciones and retenciones. */
    public record VoidRequest(String motivo, Boolean notDelivered, LocalDate reversalCommunicatedAt) {}

    private final TenantAccess access;
    private final Summaries summaries;
    private final Auditor auditor;
    private final Event<DocumentStatusChanged> statusChanged;

    public VoidingService(TenantAccess access, Summaries summaries, Auditor auditor,
            Event<DocumentStatusChanged> statusChanged, CompanyProvider companyProvider, DocumentEventProvider documentEventProvider, DocumentProvider documentProvider, TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.documentProvider = documentProvider;
        this.documentEventProvider = documentEventProvider;
        this.companyProvider = companyProvider;
        this.access = access;
        this.summaries = summaries;
        this.auditor = auditor;
        this.statusChanged = statusChanged;
    }

    /**
     * Cancels a void whose RA/RC/RR is stuck in exception (not in flight): the original goes back to
     * its previous status. A rejected voiding does this by itself.
     */
    public DocumentModel cancelVoid(UUID tenantId, UUID documentId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.OPERATOR);
            DocumentModel original = documentProvider.getDocumentForUpdate(documentId).orElse(null);
            if (original == null) {
                throw ApiException.notFound();
            }
            DocumentModel voiding = original.getVoidedByDocumentId() == null ? null : documentProvider.getDocument(original.getVoidedByDocumentId()).orElse(null);
            if (!"voiding".equals(original.getStatus()) || voiding == null || !"exception".equals(voiding.getStatus())) {
                throw ApiException.conflict("void_not_cancelable", "Solo se cancela una anulación cuyo resumen quedó con error.");
            }
            String previous = original.getStatus();
            original.setStatus(original.getStatusBeforeVoiding() == null ? "accepted" : original.getStatusBeforeVoiding());
            original.setStatusBeforeVoiding(null);
            original.setVoidedByDocumentId(null);
            original.setUpdatedAt(Instant.now());
            documentEventProvider.addDocumentEvent(original, null, "voiding " + voiding.serieNumero() + " canceled", scope.actor().auditName(), null);
            auditor.record(scope, "document.void_canceled", "document", original.getId());
            statusChanged.fire(new DocumentStatusChanged(original, previous, null));
            return original;
        });
    }

    /** Creates the RA/RC/RR and moves the original to voiding. Returns the voiding document. */
    public DocumentModel voidDocument(UUID tenantId, UUID documentId, VoidRequest request, String correlationId) {
        return QuarkusTransaction.requiringNew().call(() -> voidInTransaction(tenantId, documentId, request, correlationId, false));
    }

    /** The same, for an already-authenticated adapter client (Nubefact-compatible API). */
    public DocumentModel voidDocumentAsPlatform(UUID tenantId, UUID documentId, VoidRequest request, String correlationId) {
        return QuarkusTransaction.requiringNew().call(() -> voidInTransaction(tenantId, documentId, request, correlationId, true));
    }

    private DocumentModel voidInTransaction(UUID tenantId, UUID documentId, VoidRequest request, String correlationId, boolean platform) {
        TenantScope scope = platform ? access.enterAsPlatform(tenantId) : access.enter(tenantId, Role.OPERATOR);
        DocumentModel original = documentProvider.getDocument(documentId).orElse(null);
        if (original == null) {
            throw ApiException.notFound();
        }
        // Lock order (§10.8 rule 4): company → daily counter → documents
        CompanyModel company = companyProvider.getCompanyForShare(original.getCompanyId()).orElse(null);
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        if (tenant != null && "exited".equals(tenant.getStatus())) {
            throw ApiException.conflict("tenant_exited", "La organización dejó la plataforma: no puede anular comprobantes.");
        }
        check(original, request);
        String motivo = request.motivo().strip();
        LocalDate fecha = original.getFechaEmision();
        DocumentModel voiding;
        if (original.isGuia()) {
            throw ApiException.conflict("void_in_sol_only", "Las guías de remisión solo se dan de baja en SUNAT Operaciones en Línea (SOL).");
        } else if ("20".equals(original.getTipoComprobante()) || "40".equals(original.getTipoComprobante())) {
            // A later RR of the same type and day replaces the earlier one at SUNAT (RS 117-2017 art.
            // 38): each RR carries every reversal of that type, day and issue date made so far
            List<DocumentModel> lines = new java.util.ArrayList<>(sameDayReversals(original));
            lines.add(original);
            voiding = summaries.resumenReversiones(scope, company, original.getSunatEnv(), fecha, lines, motivo, correlationId);
            voiding.setDeadlineAt(Deadlines.endOfDay(request.reversalCommunicatedAt().plusDays(7)));
        } else if (original.isBoletaFamily()) {
            voiding = summaries.resumenDiario(scope, company, original.getSunatEnv(), fecha, List.of(original), "3", correlationId);
            voiding.setDeadlineAt(Deadlines.endOfDay(fecha.plusDays(7)));
        } else {
            voiding = summaries.comunicacionBaja(scope, company, original.getSunatEnv(), fecha, List.of(original), motivo, correlationId);
            voiding.setDeadlineAt(Deadlines.endOfDay(fecha.plusDays(7)));
        }
        voiding.setAffectedDocumentId(original.getId());
        // The conditional status change (§9.3): the original must still be accepted and undelivered
        Instant now = Instant.now();
        int moved = documentProvider.markVoiding(original.getId(), voiding.getId(), motivo,
                request.reversalCommunicatedAt() == null ? null : request.reversalCommunicatedAt().atStartOfDay(Deadlines.LIMA).toInstant());
        if (moved != 1) {
            // Rolls back the summary too: its number is not consumed
            throw ApiException.conflict("document_changed", "El documento cambió mientras se procesaba la anulación. Intente nuevamente.");
        }
        documentProvider.refresh(original);
        documentEventProvider.addDocumentEvent(original, null, "voiding requested (" + voiding.serieNumero() + "): " + motivo, scope.actor().auditName(),
                correlationId);
        auditor.record(scope, "document.void_requested", "document", original.getId(), Map.of("voiding_document", voiding.serieNumero()));
        statusChanged.fire(new DocumentStatusChanged(original, original.getStatusBeforeVoiding(), correlationId));
        voiding.setUpdatedAt(now);
        return voiding;
    }

    /** Originals of today's RRs of the same company, environment, type and issue date (voided or voiding). */
    private List<DocumentModel> sameDayReversals(DocumentModel original) {
        String serie = "RR-" + Summaries.BASIC.format(Deadlines.todayInLima());
        List<UUID> rrs = documentProvider.getReversalIds(original.getCompanyId(), original.getSunatEnv(), serie);
        if (rrs.isEmpty()) {
            return List.of();
        }
        return documentProvider.getReversedDocuments(rrs, original.getTipoComprobante(), original.getFechaEmision());
    }

    private static void check(DocumentModel original, VoidRequest request) {
        if (request == null || request.motivo() == null || request.motivo().isBlank()) {
            throw ApiException.invalid("invalid_request", "Indique el motivo de la anulación.", null);
        }
        if (original.isSummary() || "ose_inbound".equals(original.getOrigin()) || "reception".equals(original.getOrigin())) {
            throw ApiException.conflict("not_voidable", "Este documento no se anula por este medio.");
        }
        if (!DocumentModel.ACCEPTED.contains(original.getStatus()) || original.getVoidedByDocumentId() != null) {
            throw ApiException.conflict("not_voidable", "Solo se pueden anular documentos aceptados por SUNAT.");
        }
        if (original.getDeliveredAt() != null || !Boolean.TRUE.equals(request.notDelivered())) {
            throw ApiException.conflict("delivered_use_credit_note",
                    "El documento ya fue entregado al comprador (o no se confirmó que no lo fue): emita una nota de crédito.");
        }
        LocalDate today = Deadlines.todayInLima();
        if ("20".equals(original.getTipoComprobante()) || "40".equals(original.getTipoComprobante())) {
            if (request.reversalCommunicatedAt() == null) {
                throw ApiException.invalid("invalid_request", "Indique la fecha en que se comunicó la reversión al proveedor o cliente.", null);
            }
            if (request.reversalCommunicatedAt().isAfter(today) || request.reversalCommunicatedAt().isBefore(original.getFechaEmision())) {
                throw ApiException.invalid("invalid_request", "La fecha de comunicación de la reversión debe estar entre la emisión y hoy.", null);
            }
            if (!Deadlines.withinVoidingWindow(request.reversalCommunicatedAt(), today)) {
                throw ApiException.conflict("void_window_passed", "Pasaron más de 7 días desde que se comunicó la reversión.");
            }
        } else if (!Deadlines.withinVoidingWindow(original.getFechaEmision(), today)) {
            throw ApiException.conflict("void_window_passed", "Pasó el plazo de 7 días para anular: emita una nota de crédito.");
        }
    }
}
