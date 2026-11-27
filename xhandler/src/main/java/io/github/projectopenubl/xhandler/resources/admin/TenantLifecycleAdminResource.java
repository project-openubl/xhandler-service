package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.models.PrivacyProvider;
import io.github.projectopenubl.xhandler.representations.idm.admin.ArchivePlanRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.AuditHoldRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.TenantArchivePlanRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.TenantAuditHoldRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.TenantStatusRepresentation;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;

/**
 * Tenant lifecycle for platform admins (DESIGN.md §13): tax-audit holds, the archive plan and
 * cancelling an exit within its period.
 */
@Extension(name = AdminOpenApi.PROFILE_EXTENSION, value = "")
@Path("/api/admin/v1/data-protection/tenants/{tenantId}")
public class TenantLifecycleAdminResource {

    private final PlatformAdmin admin;
    private final PrivacyProvider privacy;
    private final TenantAccess access;

    public TenantLifecycleAdminResource(PlatformAdmin admin, PrivacyProvider privacy, TenantAccess access) {
        this.admin = admin;
        this.privacy = privacy;
        this.access = access;
    }

    /** A tax audit: nothing of the tenant is deleted while it holds (retention, exit). */
    @Operation(operationId = "adminSetTenantAuditHold", summary = "Activa o quita la retención por fiscalización de una organización")
    @PUT
    @Path("/audit-hold")
    @Transactional
    public TenantAuditHoldRepresentation auditHold(@PathParam("tenantId") UUID tenantId, AuditHoldRequestRepresentation body) {
        String who = admin.require();
        access.enterAsPlatform(tenantId);
        boolean hold = body != null && Boolean.TRUE.equals(body.auditHold());
        found(privacy.setAuditHold(tenantId, hold));
        AdminLog.PLATFORM_AUDIT.infof("admin %s set audit_hold=%s on tenant %s", who, hold, tenantId);
        return new TenantAuditHoldRepresentation(tenantId, hold);
    }

    @Operation(operationId = "adminSetTenantArchivePlan", summary = "Activa o quita el plan de archivo de una organización")
    @PUT
    @Path("/archive-plan")
    @Transactional
    public TenantArchivePlanRepresentation archivePlan(@PathParam("tenantId") UUID tenantId, ArchivePlanRequestRepresentation body) {
        String who = admin.require();
        access.enterAsPlatform(tenantId);
        boolean archive = body != null && Boolean.TRUE.equals(body.archivePlan());
        found(privacy.setArchivePlan(tenantId, archive));
        if (!archive) {
            // An exited tenant that drops the archive plan gets its deletion date (the 2-year limit)
            privacy.scheduleExitedTenantDeletion(tenantId);
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s set archive_plan=%s on tenant %s", who, archive, tenantId);
        return new TenantArchivePlanRepresentation(tenantId, archive);
    }

    /** The tenant comes back before its data is deleted. */
    @Operation(operationId = "adminCancelTenantExit", summary = "Cancela la salida de una organización antes de que se borren sus datos")
    @POST
    @Path("/exit/cancel")
    @Transactional
    public TenantStatusRepresentation cancelExit(@PathParam("tenantId") UUID tenantId) {
        String who = admin.require();
        access.enterAsPlatform(tenantId);
        int changed = privacy.cancelExit(tenantId);
        if (changed == 0) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s cancelled the exit of tenant %s", who, tenantId);
        return new TenantStatusRepresentation(tenantId, "active");
    }

    /** 404 when the update changed no row (unknown or deleted tenant). */
    private static void found(int changed) {
        if (changed == 0) {
            throw ApiException.notFound();
        }
    }
}
