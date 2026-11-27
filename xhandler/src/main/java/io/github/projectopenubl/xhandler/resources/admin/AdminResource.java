package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.jobs.JobStore;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.managers.DocumentControlManager;
import io.github.projectopenubl.xhandler.managers.KeyRotationManager;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.EmailProvider;
import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import io.github.projectopenubl.xhandler.models.MaintenanceProvider;
import io.github.projectopenubl.xhandler.models.PlatformProvider;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.BulkRetryRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CountRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DocumentStateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EndpointRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EndpointUpdateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.JobStateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.MigrationRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.AdminCompanyRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.AdminPartnerRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.AdminTenantRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.CompanySendingRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.EmailSuppressionRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.JobSummaryRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.KeyRotationRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.NewPartnerRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PartitionsRepairedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PartnerCreatedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PartnerMemberAssignmentRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PartnerMemberRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PartnerMemberRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.TenantSendingRepresentation;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.utils.Deadlines;
import io.github.projectopenubl.xhandler.utils.Ids;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Platform administration for operators (DESIGN.md §15, §24.1; docs/runbooks). Every call needs the
 * {@code platform-admin} role; actions on tenant data are written to that tenant's audit log.
 * Documented in the admin OpenAPI document only ({@link AdminOpenApi#PROFILE}).
 */
@Extension(name = AdminOpenApi.PROFILE_EXTENSION, value = "")
@Path("/api/admin/v1")
public class AdminResource {

    private final ModelToRepresentation modelToRepresentation;
    private final MaintenanceProvider maintenanceProvider;
    private final JobQueueProvider jobQueueProvider;
    private final EmailProvider emailProvider;
    private final TenantProvider tenantProvider;
    private final CompanyProvider companyProvider;
    private final PlatformAdmin admin;
    private final TenantAccess access;
    private final DocumentControlManager control;
    private final JobStore jobs;
    private final KeyRotationManager rotation;
    private final AuditManager auditor;
    private final PlatformProvider platform;

    public AdminResource(PlatformAdmin admin, TenantAccess access, DocumentControlManager control, JobStore jobs, KeyRotationManager rotation,
            AuditManager auditor, PlatformProvider platform, CompanyProvider companyProvider,
            TenantProvider tenantProvider, EmailProvider emailProvider, JobQueueProvider jobQueueProvider, MaintenanceProvider maintenanceProvider,
            ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.maintenanceProvider = maintenanceProvider;
        this.jobQueueProvider = jobQueueProvider;
        this.emailProvider = emailProvider;
        this.tenantProvider = tenantProvider;
        this.companyProvider = companyProvider;
        this.admin = admin;
        this.access = access;
        this.control = control;
        this.jobs = jobs;
        this.rotation = rotation;
        this.auditor = auditor;
        this.platform = platform;
    }

    // --- documents (requeue-document, retry, hold, release) ---------------------

    @Operation(operationId = "adminRetryDocument", summary = "Reintenta un documento de cualquier organización")
    @POST
    @Path("/documents/{id}/retry")
    @Transactional
    public DocumentStateRepresentation retry(@PathParam("id") UUID id) {
        return modelToRepresentation.toStateRepresentation(control.retry(openDocumentTenant(id), id));
    }

    @Operation(operationId = "adminHoldDocument", summary = "Retiene un documento de cualquier organización")
    @POST
    @Path("/documents/{id}/hold")
    @Transactional
    public DocumentStateRepresentation hold(@PathParam("id") UUID id) {
        return modelToRepresentation.toStateRepresentation(control.hold(openDocumentTenant(id), id));
    }

    @Operation(operationId = "adminReleaseDocument", summary = "Libera un documento retenido")
    @POST
    @Path("/documents/{id}/release")
    @Transactional
    public DocumentStateRepresentation release(@PathParam("id") UUID id) {
        return modelToRepresentation.toStateRepresentation(control.release(openDocumentTenant(id), id));
    }

    @Operation(operationId = "adminRetryTenantDocuments", summary = "Reintenta en bloque los documentos de una organización que cumplan el filtro")
    @POST
    @Path("/tenants/{tenantId}/retry")
    @Transactional
    public CountRepresentation bulkRetry(@PathParam("tenantId") UUID tenantId, BulkRetryRepresentation filter) {
        TenantScope scope = openTenant(tenantId);
        BulkRetryRepresentation f = filter == null ? new BulkRetryRepresentation(null, null, null, null) : filter;
        return new CountRepresentation(control.retryWhere(scope, f.companyId(), f.status(), f.from(), f.to()));
    }

    // --- tenants of the platform (§1.2): account metadata, never documents ----------

    @Operation(operationId = "adminListTenants", summary = "Organizaciones de la plataforma, las más nuevas primero (filtro por nombre o id)")
    @GET
    @Path("/tenants")
    @Transactional
    public PageRepresentation<AdminTenantRepresentation> tenants(@QueryParam("q") String q, @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        admin.require();
        int size = PageRepresentation.limit(limit);
        int from = PageRepresentation.offset(offset);
        String query = q == null ? null : q.strip();
        return new PageRepresentation<>(platform.getTenants(query, size, from).stream().map(AdminResource::tenant).toList(), size, from);
    }

    @Operation(operationId = "adminGetTenant", summary = "Una organización de la plataforma")
    @GET
    @Path("/tenants/{tenantId}")
    @Transactional
    public AdminTenantRepresentation tenant(@PathParam("tenantId") UUID tenantId) {
        admin.require();
        return tenant(platform.getTenant(tenantId).orElseThrow(ApiException::notFound));
    }

    /** The companies of a tenant, to hold them; the read is in the tenant's audit log. */
    @Operation(operationId = "adminListTenantCompanies", summary = "Empresas de una organización (queda auditado)")
    @GET
    @Path("/tenants/{tenantId}/companies")
    @Transactional
    public List<AdminCompanyRepresentation> tenantCompanies(@PathParam("tenantId") UUID tenantId) {
        TenantScope scope = openTenant(tenantId);
        List<AdminCompanyRepresentation> companies = companyProvider.getCompanies(0, 1000).stream()
                .map(c -> new AdminCompanyRepresentation(c.getId(), c.getRuc(), c.getRazonSocial(), c.getSunatEnv(), c.getEmissionMode(),
                        c.getRucVerifiedAt(), c.isSendingPaused()))
                .toList();
        auditor.record(scope, "tenant.companies_listed", "tenant", tenantId);
        return companies;
    }

    // --- holds per tenant and company ---------------------------------------------

    @Operation(operationId = "adminHoldTenantSending", summary = "Pausa los envíos a SUNAT de una organización")
    @POST
    @Path("/tenants/{tenantId}/hold")
    @Transactional
    public TenantSendingRepresentation holdTenant(@PathParam("tenantId") UUID tenantId) {
        return pauseTenant(tenantId, true);
    }

    @Operation(operationId = "adminReleaseTenantSending", summary = "Reanuda los envíos a SUNAT de una organización")
    @POST
    @Path("/tenants/{tenantId}/release")
    @Transactional
    public TenantSendingRepresentation releaseTenant(@PathParam("tenantId") UUID tenantId) {
        return pauseTenant(tenantId, false);
    }

    @Operation(operationId = "adminHoldCompanySending", summary = "Pausa los envíos a SUNAT de una empresa")
    @POST
    @Path("/tenants/{tenantId}/companies/{companyId}/hold")
    @Transactional
    public CompanySendingRepresentation holdCompany(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        return pauseCompany(tenantId, companyId, true);
    }

    @Operation(operationId = "adminReleaseCompanySending", summary = "Reanuda los envíos a SUNAT de una empresa")
    @POST
    @Path("/tenants/{tenantId}/companies/{companyId}/release")
    @Transactional
    public CompanySendingRepresentation releaseCompany(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        return pauseCompany(tenantId, companyId, false);
    }

    /** Bounce processing (§26.5): the platform's email provider reports hard bounces and complaints. */
    @Operation(operationId = "adminSuppressEmail", summary = "Suprime un correo de una organización (rebote o queja)")
    @POST
    @Path("/tenants/{tenantId}/email-suppressions")
    @Transactional
    public EmailSuppressionRepresentation suppressEmail(@PathParam("tenantId") UUID tenantId, EmailSuppressionRepresentation body) {
        TenantScope scope = openTenant(tenantId);
        String address = body == null ? null : body.address();
        if (address == null || !address.contains("@")) {
            throw ApiException.invalid("invalid_request", "Indique el correo.", null);
        }
        String reason = body.reason() == null ? "hard_bounce" : body.reason();
        emailProvider.suppressEmail(tenantId, address, reason);
        auditor.record(scope, "email.suppressed", "email", null, Map.of("address", address, "reason", reason));
        return new EmailSuppressionRepresentation(address, reason);
    }

    // --- partners (§1.2) ---------------------------------------------

    @Operation(operationId = "adminListPartners", summary = "Socios de la plataforma con sus miembros y organizaciones")
    @GET
    @Path("/partners")
    @Transactional
    public List<AdminPartnerRepresentation> partners() {
        admin.require();
        return platform.getPartners().stream()
                .map(p -> new AdminPartnerRepresentation(p.id(), p.name(), p.status(), p.createdAt(), p.members(), p.tenants()))
                .toList();
    }

    @Operation(operationId = "adminCreatePartner", summary = "Crea un socio")
    @POST
    @Path("/partners")
    @Transactional
    public PartnerCreatedRepresentation createPartner(NewPartnerRepresentation body) {
        String who = admin.require();
        String name = body == null ? null : body.name();
        if (name == null || name.isBlank()) {
            throw ApiException.invalid("invalid_request", "Indique el nombre del partner.", null);
        }
        UUID id = Ids.newId();
        platform.createPartner(id, name.strip());
        AdminLog.PLATFORM_AUDIT.infof("admin %s created partner %s (%s)", who, id, name.strip());
        return new PartnerCreatedRepresentation(id, name.strip());
    }

    @Operation(operationId = "adminListPartnerMembers", summary = "Miembros de un socio")
    @GET
    @Path("/partners/{partnerId}/members")
    @Transactional
    public List<PartnerMemberRepresentation> partnerMembers(@PathParam("partnerId") UUID partnerId) {
        admin.require();
        if (!platform.existsPartner(partnerId)) {
            throw ApiException.notFound();
        }
        return platform.getPartnerMembers(partnerId).stream()
                .map(m -> new PartnerMemberRepresentation(m.kind(), m.subject(), m.role(), m.createdAt()))
                .toList();
    }

    @Operation(operationId = "adminSetPartnerMember", summary = "Agrega o cambia el rol de un miembro de un socio")
    @PUT
    @Path("/partners/{partnerId}/members")
    @Transactional
    public PartnerMemberAssignmentRepresentation partnerMember(@PathParam("partnerId") UUID partnerId, PartnerMemberRequestRepresentation body) {
        String who = admin.require();
        String kind = body == null ? null : body.kind() == null ? "user" : body.kind();
        String subject = body == null ? null : body.subject();
        String role = body == null ? null : body.role() == null ? "admin" : body.role();
        if (subject == null || !Set.of("user", "client").contains(kind) || !Set.of("admin", "viewer").contains(role)) {
            throw ApiException.invalid("invalid_request", "Indique subject, kind (user|client) y role (admin|viewer).", null);
        }
        if (!platform.setPartnerMember(partnerId, kind, subject, role)) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s set %s:%s as %s of partner %s", who, kind, subject, role, partnerId);
        return new PartnerMemberAssignmentRepresentation(partnerId, kind, subject, role);
    }

    @Operation(operationId = "adminRemovePartnerMember", summary = "Quita un miembro de un socio")
    @APIResponse(responseCode = "204", description = "Miembro quitado.")
    @DELETE
    @Path("/partners/{partnerId}/members/{kind}/{subject}")
    @Transactional
    public Response removePartnerMember(@PathParam("partnerId") UUID partnerId, @PathParam("kind") String kind,
            @PathParam("subject") String subject) {
        String who = admin.require();
        if (!platform.removePartnerMember(partnerId, kind, subject)) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s removed %s:%s from partner %s", who, kind, subject, partnerId);
        return Response.noContent().build();
    }

    // --- endpoints (endpoint-status, hold/release per endpoint) ---------------------

    @Operation(operationId = "adminListEndpoints", summary = "Servicios de SUNAT: breaker, límite y pausa")
    @GET
    @Path("/endpoints")
    @Transactional
    public List<EndpointRepresentation> endpoints() {
        admin.require();
        return platform.getEndpoints().stream().map(AdminResource::endpoint).toList();
    }

    @Operation(operationId = "adminHoldEndpoint", summary = "Pausa un servicio de SUNAT")
    @POST
    @Path("/endpoints/{id}/hold")
    @Transactional
    public EndpointRepresentation holdEndpoint(@PathParam("id") String id) {
        admin.require();
        return endpointUpdated(id, platform.setEndpointPaused(id, true));
    }

    @Operation(operationId = "adminReleaseEndpoint", summary = "Reanuda un servicio de SUNAT")
    @POST
    @Path("/endpoints/{id}/release")
    @Transactional
    public EndpointRepresentation releaseEndpoint(@PathParam("id") String id) {
        admin.require();
        return endpointUpdated(id, platform.setEndpointPaused(id, false));
    }

    @Operation(operationId = "adminUpdateEndpoint", summary = "Cambia el límite de envíos simultáneos de un servicio de SUNAT")
    @PUT
    @Path("/endpoints/{id}")
    @Transactional
    public EndpointRepresentation setEndpoint(@PathParam("id") String id, EndpointUpdateRepresentation update) {
        if (update == null || update.concurrencyCap() == null || update.concurrencyCap() < 1) {
            throw ApiException.invalid("invalid_request", "concurrency_cap debe ser al menos 1.", null);
        }
        admin.require();
        return endpointUpdated(id, platform.setEndpointConcurrencyCap(id, update.concurrencyCap()));
    }

    // --- jobs (cancel-job, purge-jobs) ------------------------------------------------

    @Operation(operationId = "adminGetJob", summary = "Un trabajo de cualquier organización")
    @GET
    @Path("/jobs/{id}")
    @Transactional
    public JobSummaryRepresentation job(@PathParam("id") UUID id) {
        admin.require();
        PlatformProvider.JobSummary r = platform.getJobSummary(id).orElseThrow(ApiException::notFound);
        return new JobSummaryRepresentation(id, r.name(), r.state(), r.worker(), r.error());
    }

    @Operation(operationId = "adminCancelJob", summary = "Cancela un trabajo de cualquier organización")
    @POST
    @Path("/jobs/{id}/cancel")
    @Transactional
    public JobStateRepresentation cancelJob(@PathParam("id") UUID id) {
        admin.require();
        String state = platform.cancelJob(id)
                .orElseThrow(() -> ApiException.conflict("job_not_cancelable", "El trabajo no existe o ya terminó."));
        return new JobStateRepresentation(id, state);
    }

    @Operation(operationId = "adminPurgeJobs", summary = "Borra los trabajos terminados más antiguos que older_than_hours (7 días por defecto)")
    @POST
    @Path("/jobs/purge")
    @Transactional
    public CountRepresentation purgeJobs(@QueryParam("older_than_hours") Integer hours) {
        admin.require();
        return new CountRepresentation(jobs.purge(Duration.ofHours(hours == null ? 24 * 7 : Math.max(1, hours))));
    }

    // --- keys and migrations (rotate-keys, migration-status) --------------------------

    @Operation(operationId = "adminRotateKeys", summary = "Re-cifra todos los secretos y llaves con la llave maestra actual")
    @POST
    @Path("/keys/rotate")
    public KeyRotationRepresentation rotateKeys() {
        admin.require();
        KeyRotationManager.Result result = rotation.rewrapAll();
        return new KeyRotationRepresentation(result.tenants(), result.credentials(), result.signingKeys());
    }

    @Operation(operationId = "adminListMigrations", summary = "Migraciones de la base de datos aplicadas")
    @GET
    @Path("/migrations")
    @Transactional
    public List<MigrationRepresentation> migrations() {
        admin.require();
        return platform.getMigrations().stream()
                .map(m -> new MigrationRepresentation(m.version(), m.description(), m.success() ? "Success" : "Failed", m.installedOn())).toList();
    }

    // --- data repair -------------------------------------------------------------------

    /** Creates missing partitions now (normally a daily schedule). */
    @Operation(operationId = "adminRepairPartitions", summary = "Crea ahora las particiones que falten")
    @POST
    @Path("/repair/partitions")
    @Transactional
    public PartitionsRepairedRepresentation repairPartitions() {
        admin.require();
        LocalDate today = Deadlines.todayInLima();
        maintenanceProvider.ensurePartitions(today);
        return new PartitionsRepairedRepresentation(today.toString());
    }

    /** Makes due again any non-definitive document no automatic attempt would pick up. */
    @Operation(operationId = "adminRequeueStuckDocuments", summary = "Vuelve a programar los documentos atascados")
    @POST
    @Path("/repair/requeue-stuck")
    @Transactional
    public CountRepresentation requeueStuck() {
        admin.require();
        int n = platform.requeueStuckDocuments();
        notifyReconcile();
        return new CountRepresentation(n);
    }

    // ---------------------------------------------------------------------------------

    private TenantScope openDocumentTenant(UUID documentId) {
        String name = admin.require();
        UUID tenant = platform.getDocumentTenant(documentId).orElseThrow(ApiException::notFound);
        return scope(tenant, name);
    }

    private TenantScope openTenant(UUID tenantId) {
        return scope(tenantId, admin.require());
    }

    private TenantScope scope(UUID tenantId, String adminName) {
        access.enterAsPlatform(tenantId);
        if (tenantProvider.getTenant(tenantId).orElse(null) == null) {
            throw ApiException.notFound();
        }
        return new TenantScope(tenantId, new Actor("admin", adminName.substring("admin:".length()), false), Role.PLATFORM);
    }

    private TenantSendingRepresentation pauseTenant(UUID tenantId, boolean paused) {
        TenantScope scope = openTenant(tenantId);
        TenantModel tenant = tenantProvider.getTenantForUpdate(tenantId).orElseThrow(ApiException::notFound);
        tenant.setSendingPaused(paused);
        auditor.record(scope, paused ? "tenant.sending_held" : "tenant.sending_released", "tenant", tenantId);
        notifyReconcile();
        return new TenantSendingRepresentation(tenantId, paused);
    }

    private CompanySendingRepresentation pauseCompany(UUID tenantId, UUID companyId, boolean paused) {
        TenantScope scope = openTenant(tenantId);
        CompanyModel company = companyProvider.getCompanyForUpdate(companyId).orElse(null);
        if (company == null) {
            throw ApiException.notFound();
        }
        company.setSendingPaused(paused);
        auditor.record(scope, paused ? "company.sending_held" : "company.sending_released", "company", companyId);
        notifyReconcile();
        return new CompanySendingRepresentation(companyId, paused);
    }

    /** After an endpoint update ({@code admin.require()} already called): 404 when unknown, else wakes the reconciler. */
    private EndpointRepresentation endpointUpdated(String id, int updated) {
        if (updated == 0) {
            throw ApiException.notFound();
        }
        notifyReconcile();
        return endpoint(platform.getEndpoint(id));
    }

    private void notifyReconcile() {
        jobQueueProvider.notifyReconcile();
    }

    private static EndpointRepresentation endpoint(PlatformProvider.EndpointRow r) {
        return new EndpointRepresentation(r.id(), r.breakerState(), r.breakerUntil(), r.concurrencyCap(), r.paused());
    }

    private static AdminTenantRepresentation tenant(PlatformProvider.TenantRow t) {
        return new AdminTenantRepresentation(t.id(), t.name(), t.status(), t.partnerId(), t.partnerName(), t.createdBy(),
                t.createdAt(), t.sendingPaused(), t.auditHold(), t.archivePlan(), t.deleteAfter(), t.companies());
    }
}
