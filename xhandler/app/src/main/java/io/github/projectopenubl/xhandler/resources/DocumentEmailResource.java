package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.jobs.handlers.DocumentEmailJob;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.models.jpa.entities.CompanyEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.models.jpa.entities.JobEntity;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
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

    public record EmailRequest(@NotEmpty @Size(max = 10) List<String> to, @Size(max = 10) List<String> cc, Boolean includeCdr) {}

    public record Queued(UUID jobId, String jobPrn) {}

    static final String EMAIL = "[^@\\s<>\"]+@[^@\\s<>\"]+\\.[A-Za-z]{2,}";

    private final TenantAccess access;
    private final Jobs jobs;
    private final Auditor auditor;
    private final EntityManager em;
    private final long maxPerDay;

    public DocumentEmailResource(TenantAccess access, Jobs jobs, Auditor auditor, EntityManager em,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.mail.max-per-tenant-per-day", defaultValue = "2000")
                    long maxPerDay) {
        this.maxPerDay = maxPerDay;
        this.access = access;
        this.jobs = jobs;
        this.auditor = auditor;
        this.em = em;
    }

    @APIResponse(responseCode = "202", description = "Correo encolado: lo envía un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Queued.class)))
    @Operation(operationId = "emailDocument")
    @POST
    @Transactional
    public Response send(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId, @Valid EmailRequest request) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        DocumentEntity doc = DocumentEntity.findById(documentId);
        if (doc == null) {
            throw ApiException.notFound();
        }
        CompanyEntity company = CompanyEntity.findById(doc.getCompanyId());
        if (!"production".equals(doc.getSunatEnv()) && company.getRucVerifiedAt() == null) {
            // Test documents from an unverified company (any RUC) must not reach third parties
            throw ApiException.conflict("ruc_not_verified", "Verifique el RUC de la empresa para enviar documentos de prueba por correo.");
        }
        long sentToday = ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM xhandler.delivery WHERE channel = 'email' AND at > now() - interval '1 day'").getSingleResult()).longValue();
        if (sentToday >= maxPerDay) {
            throw new ApiException(429, "email_quota", "Alcanzó el máximo de correos del día.");
        }
        List<String> cc = request.cc() == null ? List.of() : request.cc();
        for (String address : concat(request.to(), cc)) {
            if (!address.matches(EMAIL)) {
                throw ApiException.invalid("invalid_request", "Correo inválido: " + address, null);
            }
        }
        List<String> suppressed = request.to().stream().filter(a -> EmailSuppressions.suppressed(em, a)).toList();
        cc = cc.stream().filter(a -> !EmailSuppressions.suppressed(em, a)).toList();
        if (suppressed.size() == request.to().size()) {
            throw ApiException.invalid("email_suppressed", "Los destinatarios rebotaron antes y no se les envía correo.", String.join(", ", suppressed));
        }
        JobEntity job = jobs.enqueue(scope, DocumentEmailJob.NAME, 3, Map.of("document_id", documentId.toString(), "to", request.to(), "cc", cc,
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
        DocumentEntity doc = change.document();
        if (!DocumentEntity.ACCEPTED.contains(doc.getStatus()) || DocumentEntity.ACCEPTED.contains(change.previousStatus()) || doc.isSummary()
                || !"production".equals(doc.getSunatEnv())) {
            return;
        }
        CompanyEntity company = CompanyEntity.findById(doc.getCompanyId());
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
