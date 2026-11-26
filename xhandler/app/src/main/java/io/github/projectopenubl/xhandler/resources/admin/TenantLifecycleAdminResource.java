package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.security.PlatformAdmin;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.resources.DataSubjectRequests;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.models.PrivacyProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant lifecycle and data protection for platform admins (DESIGN.md §13, §16): tax-audit holds,
 * the archive plan, cancelling an exit within its period, and data-subject requests that reached
 * the platform (forwarded to the tenant at once).
 */
@Path("/api/admin/v1/data-protection/tenants/{tenantId}")
public class TenantLifecycleAdminResource {

    private final PlatformAdmin admin;
    private final PrivacyProvider privacy;
    private final TenantAccess access;
    private final DataSubjectRequests requests;

    public TenantLifecycleAdminResource(PlatformAdmin admin, PrivacyProvider privacy, TenantAccess access, DataSubjectRequests requests) {
        this.admin = admin;
        this.privacy = privacy;
        this.access = access;
        this.requests = requests;
    }

    /** A tax audit: nothing of the tenant is deleted while it holds (retention, exit). */
    @PUT
    @Path("/audit-hold")
    @Transactional
    public Map<String, Object> auditHold(@PathParam("tenantId") UUID tenantId, Map<String, Boolean> body) {
        String who = admin.require();
        access.enterAsPlatform(tenantId);
        boolean hold = body != null && Boolean.TRUE.equals(body.get("audit_hold"));
        found(privacy.setAuditHold(tenantId, hold));
        AdminLog.PLATFORM_AUDIT.infof("admin %s set audit_hold=%s on tenant %s", who, hold, tenantId);
        return Map.of("tenant_id", tenantId, "audit_hold", hold);
    }

    @PUT
    @Path("/archive-plan")
    @Transactional
    public Map<String, Object> archivePlan(@PathParam("tenantId") UUID tenantId, Map<String, Boolean> body) {
        String who = admin.require();
        access.enterAsPlatform(tenantId);
        boolean archive = body != null && Boolean.TRUE.equals(body.get("archive_plan"));
        found(privacy.setArchivePlan(tenantId, archive));
        if (!archive) {
            // An exited tenant that drops the archive plan gets its deletion date (the 2-year limit)
            privacy.scheduleExitedTenantDeletion(tenantId);
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s set archive_plan=%s on tenant %s", who, archive, tenantId);
        return Map.of("tenant_id", tenantId, "archive_plan", archive);
    }

    /** The tenant comes back before its data is deleted. */
    @POST
    @Path("/exit/cancel")
    @Transactional
    public Map<String, Object> cancelExit(@PathParam("tenantId") UUID tenantId) {
        String who = admin.require();
        access.enterAsPlatform(tenantId);
        int changed = privacy.cancelExit(tenantId);
        if (changed == 0) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s cancelled the exit of tenant %s", who, tenantId);
        return Map.of("tenant_id", tenantId, "status", "active");
    }

    /** A request a person sent to the platform about a tenant's data: recorded and forwarded to the tenant. */
    @POST
    @Path("/data-subject-requests")
    @Transactional
    public Map<String, Object> dataSubjectRequest(@PathParam("tenantId") UUID tenantId, @Valid DataSubjectRequests.NewRequest request) {
        String who = admin.require();
        // Platform admins act on any tenant: opened explicitly, after the platform check
        TenantScope scope = access.enterAsPlatform(tenantId);
        if (!privacy.existsTenant(tenantId)) {
            throw ApiException.notFound();
        }
        UUID id = requests.register(scope, request, "platform");
        AdminLog.PLATFORM_AUDIT.infof("admin %s registered data-subject request %s for tenant %s", who, id, tenantId);
        return Map.of("id", id, "tenant_id", tenantId, "forwarded", true);
    }

    /** 404 when the update changed no row (unknown or deleted tenant). */
    private static void found(int changed) {
        if (changed == 0) {
            throw ApiException.notFound();
        }
    }
}
