package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.DocumentEventModel;
import io.github.projectopenubl.xhandler.utils.Deadlines;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manual control of SUNAT attempts (DESIGN.md §24.1 rule 8): hold, release and retry, per document
 * or in bulk. The same actions back the tenant API and xhandler-admin. Every method runs with the
 * document's tenant open and changes rows under a row lock.
 */
@ApplicationScoped
public class DocumentControl {

    private final DocumentProvider documentProvider;

    private final DocumentEventProvider documentEventProvider;

    /** Statuses a manual retry accepts: non-definitive ones, and exception (resend with the same number). */
    static final Set<String> RETRY_FROM = Set.of("pending_send", "in_progress", "unknown", "exception");

    private final Auditor auditor;
    private final io.github.projectopenubl.xhandler.jobs.JobStore jobs;
    private final Event<DocumentStatusChanged> statusChanged;

    public DocumentControl(Auditor auditor, Event<DocumentStatusChanged> statusChanged,
            io.github.projectopenubl.xhandler.jobs.JobStore jobs, DocumentEventProvider documentEventProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.documentEventProvider = documentEventProvider;
        this.auditor = auditor;
        this.jobs = jobs;
        this.statusChanged = statusChanged;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel lock(UUID documentId) {
        DocumentModel doc = documentProvider.getDocumentForUpdate(documentId).orElse(null);
        if (doc == null) {
            throw ApiException.notFound();
        }
        return doc;
    }

    /** Stops automatic attempts until released (hold_reason = operator). */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel hold(TenantScope scope, UUID documentId) {
        DocumentModel doc = lock(documentId);
        if (doc.definitive()) {
            throw ApiException.conflict("document_final", "El documento ya tiene una respuesta definitiva.");
        }
        doc.setHoldReason("operator");
        doc.setUpdatedAt(Instant.now());
        documentEventProvider.addDocumentEvent(doc, null, "held", scope.actor().auditName(), null);
        auditor.record(scope, "document.held", "document", doc.getId());
        return doc;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel release(TenantScope scope, UUID documentId) {
        DocumentModel doc = lock(documentId);
        if (doc.getHoldReason() == null) {
            return doc;
        }
        doc.setHoldReason(null);
        if (DocumentModel.RETRYABLE.contains(doc.getStatus())) {
            doc.setNextAttemptAt(Instant.now());
            notifyReconciler();
        }
        doc.setUpdatedAt(Instant.now());
        documentEventProvider.addDocumentEvent(doc, null, "released", scope.actor().auditName(), null);
        auditor.record(scope, "document.released", "document", doc.getId());
        return doc;
    }

    /**
     * Clears the hold and attempts now. A document in {@code exception} goes back to {@code
     * pending_send} and is resent with the same number and bytes, once the user fixed the cause.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel retry(TenantScope scope, UUID documentId) {
        DocumentModel doc = lock(documentId);
        if (doc.getSummaryDocumentId() != null) {
            // In a resumen diario: only once that summary failed. It leaves the summary and goes
            // to SUNAT again (past D+5 that means a new resumen diario, §9.2.4)
            DocumentModel summary = documentProvider.getDocument(doc.getSummaryDocumentId()).orElse(null);
            if (summary != null && !"rejected".equals(summary.getStatus()) && !"exception".equals(summary.getStatus())) {
                throw ApiException.conflict("document_in_summary", "El documento sigue el resultado de su resumen diario.");
            }
            doc.setSummaryDocumentId(null);
        }
        if (!RETRY_FROM.contains(doc.getStatus()) || "ticket_lost".equals(doc.getCodeClass())) {
            // A summary resolved line by line must not be resent: its lines went to new summaries
            throw ApiException.conflict("document_not_retryable", "El documento no se puede reenviar en su estado actual.");
        }
        retryLocked(scope, doc);
        if (doc.isBoletaFamily() && Deadlines.boletaPastIndividualWindow(doc.getFechaEmision(), Deadlines.todayInLima())) {
            // Past D+5 a boleta can't go on its own: the resumen diario fallback takes it now
            doc.setStatus("pending_send");
            doc.setCodeClass("route_change");
            doc.setNextAttemptAt(null);
            jobs.enqueuePlatform("documents.boleta-summary-fallback", 2, Map.of("trigger", "retry"),
                    List.of("schedule:boleta-summary-fallback"));
        }
        notifyReconciler();
        return doc;
    }

    /** Bulk retry in the open tenant by filter (§24.1). Returns how many documents were retried. */
    @Transactional(Transactional.TxType.MANDATORY)
    public int retryWhere(TenantScope scope, UUID companyId, String status, LocalDate from, LocalDate to) {
        List<DocumentModel> docs = documentProvider.getDocumentsToRetryForUpdate(status == null ? RETRY_FROM : Set.of(status), companyId, from, to);
        int n = 0;
        for (DocumentModel doc : docs) {
            if (RETRY_FROM.contains(doc.getStatus())) {
                retryLocked(scope, doc);
                n++;
            }
        }
        auditor.record(scope, "document.bulk_retry", "tenant", scope.tenantId(),
                Map.of("count", n, "status", String.valueOf(status), "company_id", String.valueOf(companyId)));
        if (n > 0) {
            notifyReconciler();
        }
        return n;
    }

    private void retryLocked(TenantScope scope, DocumentModel doc) {
        String previous = doc.getStatus();
        Instant now = Instant.now();
        if ("exception".equals(doc.getStatus())) {
            doc.setStatus("pending_send");
        }
        doc.setHoldReason(null);
        doc.setNextAttemptAt(now);
        doc.setSameOutcomeCount(0);
        doc.setLastOutcome(null);
        if ("summary_failed".equals(doc.getCodeClass())) {
            doc.setCodeClass(null);
        }
        doc.setProgressAt(now);
        doc.setUpdatedAt(now);
        documentEventProvider.addDocumentEvent(doc, null, "manual retry", scope.actor().auditName(), null);
        auditor.record(scope, "document.retried", "document", doc.getId());
        if (!doc.getStatus().equals(previous)) {
            statusChanged.fire(new DocumentStatusChanged(doc, previous, null));
        }
    }

    private void notifyReconciler() {
        documentProvider.notifyReconcile();
    }
}
