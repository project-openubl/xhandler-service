package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.jobs.handlers.DocumentEmailJob;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.models.JobModel;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.event.Observes;
import io.github.projectopenubl.xhandler.models.EmailProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Emailing documents to buyers (DESIGN.md §11.6): on request (202 + job), or when SUNAT accepts. */
@Path("/api/v1/tenants/{tenantId}/documents/{documentId}/email")
public class DocumentEmailResource {

    private final DocumentProvider documentProvider;

    private final CompanyProvider companyProvider;

    public record EmailRequest(@NotEmpty @Size(max = 10) List<String> to, @Size(max = 10) List<String> cc, Boolean includeCdr) {}

    public record Queued(UUID jobId, String jobPrn) {}

    static final String EMAIL = "[^@\\s<>\"]+@[^@\\s<>\"]+\\.[A-Za-z]{2,}";

    private final TenantAccess access;
    private final Jobs jobs;
    private final AuditManager auditor;
    private final EmailProvider emailProvider;
    private final long maxPerDay;

    public DocumentEmailResource(TenantAccess access, Jobs jobs, AuditManager auditor, EmailProvider emailProvider,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.mail.max-per-tenant-per-day", defaultValue = "2000")
                    long maxPerDay, CompanyProvider companyProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.companyProvider = companyProvider;
        this.maxPerDay = maxPerDay;
        this.access = access;
        this.jobs = jobs;
        this.auditor = auditor;
        this.emailProvider = emailProvider;
    }

    @APIResponse(responseCode = "202", description = "Correo encolado: lo envía un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Queued.class)))
    @Operation(operationId = "emailDocument")
    @POST
    @Transactional
    public Response send(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId, @Valid EmailRequest request) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        DocumentModel doc = documentProvider.getDocument(documentId).orElse(null);
        if (doc == null) {
            throw ApiException.notFound();
        }
        CompanyModel company = companyProvider.getCompany(doc.getCompanyId()).orElse(null);
        if (!"production".equals(doc.getSunatEnv()) && company.getRucVerifiedAt() == null) {
            // Test documents from an unverified company (any RUC) must not reach third parties
            throw ApiException.conflict("ruc_not_verified", "Verifique el RUC de la empresa para enviar documentos de prueba por correo.");
        }
        long sentToday = emailProvider.countEmailsSentLastDay();
        if (sentToday >= maxPerDay) {
            throw new ApiException(429, "email_quota", "Alcanzó el máximo de correos del día.");
        }
        List<String> cc = request.cc() == null ? List.of() : request.cc();
        for (String address : concat(request.to(), cc)) {
            if (!address.matches(EMAIL)) {
                throw ApiException.invalid("invalid_request", "Correo inválido: " + address, null);
            }
        }
        List<String> suppressed = request.to().stream().filter(a -> emailProvider.isEmailSuppressed(a)).toList();
        cc = cc.stream().filter(a -> !emailProvider.isEmailSuppressed(a)).toList();
        if (suppressed.size() == request.to().size()) {
            throw ApiException.invalid("email_suppressed", "Los destinatarios rebotaron antes y no se les envía correo.", String.join(", ", suppressed));
        }
        JobModel job = jobs.enqueue(scope, DocumentEmailJob.NAME, 3, Map.of("document_id", documentId.toString(), "to", request.to(), "cc", cc,
                "include_cdr", Boolean.TRUE.equals(request.includeCdr())), List.of(), null);
        auditor.record(scope, "document.email_requested", "document", documentId, Map.of("to", String.join(",", request.to())));
        return Response.status(202).entity(new Queued(job.getId(), "xh:job:" + job.getId())).build();
    }

    /**
     * Companies that ask for it email the buyer once SUNAT accepts (production documents only). The
     * job finds the buyer's address: this runs inside the transaction that recorded SUNAT's answer,
     * so it does no I/O (§10.8).
     */
    void onAccepted(@Observes DocumentStatusChanged change) {
        DocumentModel doc = change.document();
        if (!DocumentModel.ACCEPTED.contains(doc.getStatus()) || DocumentModel.ACCEPTED.contains(change.previousStatus()) || doc.isSummary()
                || !"production".equals(doc.getSunatEnv())) {
            return;
        }
        CompanyModel company = companyProvider.getCompany(doc.getCompanyId()).orElse(null);
        if (company == null || !company.isEmailOnAccept()) {
            return;
        }
        jobs.enqueue(new TenantScope(doc.getTenantId(), Actor.PLATFORM, Role.PLATFORM), DocumentEmailJob.NAME, 3,
                Map.of("document_id", doc.getId().toString(), "to_customer", true, "to", List.of(), "cc", List.of(), "include_cdr", false),
                List.of(), change.correlationId());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
