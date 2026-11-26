package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.MigrationRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.JobStateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EndpointUpdateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EndpointRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DocumentStateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CountRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.BulkRetryRepresentation;
import io.github.projectopenubl.xhandler.models.MaintenanceProvider;
import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import io.github.projectopenubl.xhandler.models.EmailProvider;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.PlatformProvider;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.managers.DocumentControlManager;
import io.github.projectopenubl.xhandler.jobs.JobStore;
import io.github.projectopenubl.xhandler.managers.KeyRotationManager;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform administration, used by xhandler-admin (DESIGN.md §15, §24.1). Every call needs the
 * {@code platform-admin} role; actions on tenant data are written to that tenant's audit log.
 */
@Path("/api/admin/v1")
public class AdminResource {

    private final ModelToRepresentation modelToRepresentation;

    private final MaintenanceProvider maintenanceProvider;

    private final JobQueueProvider jobQueueProvider;

    private final EmailProvider emailProvider;

    private final TenantProvider tenantProvider;

    private final CompanyProvider companyProvider;

    /** Platform-level admin actions (not tied to one tenant's audit log) go to this logger. */








    private final PlatformAdmin admin;
    private final TenantAccess access;
    private final DocumentControlManager control;
    private final JobStore jobs;
    private final KeyRotationManager rotation;
    private final AuditManager auditor;
    private final PlatformProvider platform;
    private final io.github.projectopenubl.xhandler.managers.EntitlementManager entitlements;

    public AdminResource(PlatformAdmin admin, TenantAccess access, DocumentControlManager control, JobStore jobs, KeyRotationManager rotation,
            AuditManager auditor, PlatformProvider platform, io.github.projectopenubl.xhandler.managers.EntitlementManager entitlements, CompanyProvider companyProvider, TenantProvider tenantProvider, EmailProvider emailProvider, JobQueueProvider jobQueueProvider, MaintenanceProvider maintenanceProvider, ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.maintenanceProvider = maintenanceProvider;
        this.jobQueueProvider = jobQueueProvider;
        this.emailProvider = emailProvider;
        this.tenantProvider = tenantProvider;
        this.companyProvider = companyProvider;
        this.entitlements = entitlements;
        this.admin = admin;
        this.access = access;
        this.control = control;
        this.jobs = jobs;
        this.rotation = rotation;
        this.auditor = auditor;
        this.platform = platform;
    }

    // --- documents (requeue-document, retry, hold, release) ---------------------

    @POST
    @Path("/documents/{id}/retry")
    @Transactional
    public DocumentStateRepresentation retry(@PathParam("id") UUID id) {
        return modelToRepresentation.toStateRepresentation(control.retry(openDocumentTenant(id), id));
    }

    @POST
    @Path("/documents/{id}/hold")
    @Transactional
    public DocumentStateRepresentation hold(@PathParam("id") UUID id) {
        return modelToRepresentation.toStateRepresentation(control.hold(openDocumentTenant(id), id));
    }

    @POST
    @Path("/documents/{id}/release")
    @Transactional
    public DocumentStateRepresentation release(@PathParam("id") UUID id) {
        return modelToRepresentation.toStateRepresentation(control.release(openDocumentTenant(id), id));
    }

    @POST
    @Path("/tenants/{tenantId}/retry")
    @Transactional
    public CountRepresentation bulkRetry(@PathParam("tenantId") UUID tenantId, BulkRetryRepresentation filter) {
        TenantScope scope = openTenant(tenantId);
        BulkRetryRepresentation f = filter == null ? new BulkRetryRepresentation(null, null, null, null) : filter;
        return new CountRepresentation(control.retryWhere(scope, f.companyId(), f.status(), f.from(), f.to()));
    }

    // --- holds per tenant and company ---------------------------------------------

    @POST
    @Path("/tenants/{tenantId}/hold")
    @Transactional
    public Map<String, Object> holdTenant(@PathParam("tenantId") UUID tenantId) {
        return pauseTenant(tenantId, true);
    }

    @POST
    @Path("/tenants/{tenantId}/release")
    @Transactional
    public Map<String, Object> releaseTenant(@PathParam("tenantId") UUID tenantId) {
        return pauseTenant(tenantId, false);
    }

    @POST
    @Path("/tenants/{tenantId}/companies/{companyId}/hold")
    @Transactional
    public Map<String, Object> holdCompany(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        return pauseCompany(tenantId, companyId, true);
    }

    @POST
    @Path("/tenants/{tenantId}/companies/{companyId}/release")
    @Transactional
    public Map<String, Object> releaseCompany(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        return pauseCompany(tenantId, companyId, false);
    }

    /** The OSE obligation is a regulatory status set by the platform (§3.2), not by the tenant. */
    @PUT
    @Path("/tenants/{tenantId}/companies/{companyId}/ose-obligated")
    @Transactional
    public Map<String, Object> oseObligated(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            Map<String, Boolean> body) {
        TenantScope scope = openTenant(tenantId);
        CompanyModel company = companyProvider.getCompanyForUpdate(companyId).orElse(null);
        if (company == null) {
            throw ApiException.notFound();
        }
        if (body == null || !body.containsKey("ose_obligated") || body.get("ose_obligated") == null) {
            throw ApiException.invalid("invalid_request", "Indique ose_obligated (true o false).", null);
        }
        boolean value = body.get("ose_obligated");
        company.setOseObligated(value);
        auditor.record(scope, "company.ose_obligated", "company", companyId, Map.of("value", value));
        return Map.of("company_id", companyId, "ose_obligated", value);
    }

    /** Bounce processing (§26.5): the platform's email provider reports hard bounces and complaints. */
    @POST
    @Path("/tenants/{tenantId}/email-suppressions")
    @Transactional
    public Map<String, Object> suppressEmail(@PathParam("tenantId") UUID tenantId, Map<String, String> body) {
        TenantScope scope = openTenant(tenantId);
        String address = body == null ? null : body.get("address");
        if (address == null || !address.contains("@")) {
            throw ApiException.invalid("invalid_request", "Indique el correo.", null);
        }
        String reason = body.getOrDefault("reason", "hard_bounce");
        emailProvider.suppressEmail(tenantId, address, reason);
        auditor.record(scope, "email.suppressed", "email", null, Map.of("address", address, "reason", reason));
        return Map.of("address", address, "reason", reason);
    }

    // --- partners and plans (§1.2, §14) ---------------------------------------------

    @POST
    @Path("/partners")
    @Transactional
    public Map<String, Object> createPartner(Map<String, String> body) {
        admin.require();
        String name = body == null ? null : body.get("name");
        if (name == null || name.isBlank()) {
            throw ApiException.invalid("invalid_request", "Indique el nombre del partner.", null);
        }
        UUID id = io.github.projectopenubl.xhandler.utils.Ids.newId();
        platform.createPartner(id, name.strip());
        AdminLog.PLATFORM_AUDIT.infof("admin %s created partner %s (%s)", admin.require(), id, name.strip());
        return Map.of("id", id, "name", name.strip());
    }

    @PUT
    @Path("/partners/{partnerId}/members")
    @Transactional
    public Map<String, Object> partnerMember(@PathParam("partnerId") UUID partnerId, Map<String, String> body) {
        admin.require();
        String kind = body == null ? null : body.getOrDefault("kind", "user");
        String subject = body == null ? null : body.get("subject");
        String role = body == null ? null : body.getOrDefault("role", "admin");
        if (subject == null || !java.util.Set.of("user", "client").contains(kind) || !java.util.Set.of("admin", "viewer").contains(role)) {
            throw ApiException.invalid("invalid_request", "Indique subject, kind (user|client) y role (admin|viewer).", null);
        }
        if (!platform.setPartnerMember(partnerId, kind, subject, role)) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s set %s:%s as %s of partner %s", admin.require(), kind, subject, role, partnerId);
        return Map.of("partner_id", partnerId, "kind", kind, "subject", subject, "role", role);
    }

    @jakarta.ws.rs.DELETE
    @Path("/partners/{partnerId}/members/{kind}/{subject}")
    @Transactional
    public jakarta.ws.rs.core.Response removePartnerMember(@PathParam("partnerId") UUID partnerId, @PathParam("kind") String kind,
            @PathParam("subject") String subject) {
        String who = admin.require();
        if (!platform.removePartnerMember(partnerId, kind, subject)) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s removed %s:%s from partner %s", who, kind, subject, partnerId);
        return jakarta.ws.rs.core.Response.noContent().build();
    }

    /** The company that issues the platform's own invoices (self-invoicing, §14). */
    @PUT
    @Path("/billing/issuer")
    @Transactional
    public Map<String, Object> billingIssuer(Map<String, String> body) {
        admin.require();
        String tenant = body == null ? null : body.get("tenant_id");
        String company = body == null ? null : body.get("company_id");
        if (tenant == null || company == null) {
            throw ApiException.invalid("invalid_request", "Indique tenant_id y company_id.", null);
        }
        UUID tenantId;
        UUID companyId;
        try {
            tenantId = UUID.fromString(tenant);
            companyId = UUID.fromString(company);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("invalid_request", "tenant_id y company_id deben ser UUID.", null);
        }
        access.enterAsPlatform(tenantId);
        if (companyProvider.getCompany(companyId).orElse(null) == null) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s set the billing issuer to company %s of tenant %s", admin.require(), companyId, tenantId);
        for (Map.Entry<String, String> e : Map.of(io.github.projectopenubl.xhandler.managers.SelfInvoicingManager.ISSUER_TENANT, tenant,
                io.github.projectopenubl.xhandler.managers.SelfInvoicingManager.ISSUER_COMPANY, company).entrySet()) {
            platform.setPlatformSetting(e.getKey(), e.getValue());
        }
        return Map.of("tenant_id", tenant, "company_id", company);
    }

    /** Sets a tenant's plan by hand (contracts outside Lago); Lago syncs don't overwrite it. */
    @PUT
    @Path("/tenants/{tenantId}/plan")
    @Transactional
    public Map<String, Object> plan(@PathParam("tenantId") UUID tenantId, Map<String, String> body) {
        TenantScope scope = openTenant(tenantId);
        String code = body == null ? null : body.get("plan");
        if (code == null) {
            throw ApiException.invalid("invalid_request", "Indique el plan.", null);
        }
        entitlements.setPlan(tenantId, code, "admin");
        auditor.record(scope, "tenant.plan_set", "tenant", tenantId, Map.of("plan", code));
        return Map.of("tenant_id", tenantId, "plan", code);
    }

    // --- endpoints (endpoint-status, hold/release per endpoint) ---------------------

    @GET
    @Path("/endpoints")
    @Transactional
    public List<EndpointRepresentation> endpoints() {
        admin.require();
        return platform.getEndpoints().stream().map(AdminResource::endpoint).toList();
    }

    @POST
    @Path("/endpoints/{id}/hold")
    @Transactional
    public EndpointRepresentation holdEndpoint(@PathParam("id") String id) {
        admin.require();
        return endpointUpdated(id, platform.setEndpointPaused(id, true));
    }

    @POST
    @Path("/endpoints/{id}/release")
    @Transactional
    public EndpointRepresentation releaseEndpoint(@PathParam("id") String id) {
        admin.require();
        return endpointUpdated(id, platform.setEndpointPaused(id, false));
    }

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

    @POST
    @Path("/jobs/{id}/cancel")
    @Transactional
    public JobStateRepresentation cancelJob(@PathParam("id") UUID id) {
        admin.require();
        String state = platform.cancelJob(id)
                .orElseThrow(() -> ApiException.conflict("job_not_cancelable", "El trabajo no existe o ya terminó."));
        return new JobStateRepresentation(id, state);
    }

    @POST
    @Path("/jobs/purge")
    @Transactional
    public CountRepresentation purgeJobs(@jakarta.ws.rs.QueryParam("older_than_hours") Integer hours) {
        admin.require();
        return new CountRepresentation(jobs.purge(java.time.Duration.ofHours(hours == null ? 24 * 7 : Math.max(1, hours))));
    }

    // --- keys and migrations (rotate-keys, migration-status) --------------------------

    @POST
    @Path("/keys/rotate")
    public KeyRotationManager.Result rotateKeys() {
        admin.require();
        return rotation.rewrapAll();
    }

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
    @POST
    @Path("/repair/partitions")
    @Transactional
    public Map<String, Object> repairPartitions() {
        admin.require();
        LocalDate today = io.github.projectopenubl.xhandler.utils.Deadlines.todayInLima();
        maintenanceProvider.ensurePartitions(today);
        return Map.of("day", today.toString());
    }

    /** Makes due again any non-definitive document no automatic attempt would pick up. */
    @POST
    @Path("/repair/requeue-stuck")
    @Transactional
    public CountRepresentation requeueStuck() {
        admin.require();
        int n = platform.requeueStuckDocuments();
        notifyReconcile();
        return new CountRepresentation(n);
    }

    @GET
    @Path("/jobs/{id}")
    @Transactional
    public Map<String, Object> job(@PathParam("id") UUID id) {
        admin.require();
        PlatformProvider.JobSummary r = platform.getJobSummary(id).orElseThrow(ApiException::notFound);
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", id);
        view.put("name", r.name());
        view.put("state", r.state());
        view.put("worker", r.worker());
        view.put("error", r.error());
        return view;
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
        return new TenantScope(tenantId, new Actor("admin", adminName.substring("admin:".length()), false, false), Role.PLATFORM);
    }

    private Map<String, Object> pauseTenant(UUID tenantId, boolean paused) {
        TenantScope scope = openTenant(tenantId);
        TenantModel tenant = tenantProvider.getTenantForUpdate(tenantId).orElse(null);
        tenant.setSendingPaused(paused);
        auditor.record(scope, paused ? "tenant.sending_held" : "tenant.sending_released", "tenant", tenantId);
        notifyReconcile();
        return Map.of("tenant_id", tenantId, "sending_paused", paused);
    }

    private Map<String, Object> pauseCompany(UUID tenantId, UUID companyId, boolean paused) {
        TenantScope scope = openTenant(tenantId);
        CompanyModel company = companyProvider.getCompanyForUpdate(companyId).orElse(null);
        if (company == null) {
            throw ApiException.notFound();
        }
        company.setSendingPaused(paused);
        auditor.record(scope, paused ? "company.sending_held" : "company.sending_released", "company", companyId);
        notifyReconcile();
        return Map.of("company_id", companyId, "sending_paused", paused);
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

}
