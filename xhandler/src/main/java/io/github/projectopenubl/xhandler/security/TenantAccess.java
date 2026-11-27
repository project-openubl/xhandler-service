package io.github.projectopenubl.xhandler.security;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.MembershipProvider;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Opens a tenant for the current transaction: checks the caller's role, then sets {@code
 * app.tenant_id} so row-level security shows only that tenant's rows (DESIGN.md §6, §10.8).
 *
 * <p>Call it first in every {@code @Transactional} method that touches tenant data. Unknown tenants
 * and tenants the caller doesn't belong to are both 404, so ids don't leak.
 */
@ApplicationScoped
public class TenantAccess {

    private final MembershipProvider membership;
    private final CurrentActor currentActor;

    public TenantAccess(MembershipProvider membership, CurrentActor currentActor) {
        this.membership = membership;
        this.currentActor = currentActor;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public TenantScope enter(UUID tenantId, Role minimum) {
        Actor actor = currentActor.get();
        Role role = roleOf(tenantId, actor);
        if (role == null) {
            throw ApiException.notFound();
        }
        if (!role.allows(minimum)) {
            throw ApiException.forbidden();
        }
        setTenant(tenantId);
        return new TenantScope(tenantId, actor, role);
    }

    /**
     * Opens a tenant without a caller, for platform work (key rotation, schedules). Never reachable
     * from a request without an explicit platform check.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public TenantScope enterAsPlatform(UUID tenantId) {
        setTenant(tenantId);
        return new TenantScope(tenantId, Actor.PLATFORM, Role.PLATFORM);
    }

    private Role roleOf(UUID tenantId, Actor actor) {
        String role = membership.getMemberRole(tenantId, actor.kind(), actor.subject()).orElse(null);
        // Partners manage their tenants (DESIGN.md §1.2): partner admins administer, viewers see.
        // The more privileged of the tenant and partner roles applies.
        String partnerRole = membership.getPartnerRole(tenantId, actor.kind(), actor.subject()).orElse(null);
        Role tenantRole = role == null ? null : Role.fromDb(role);
        Role viaPartner = partnerRole == null ? null : Role.fromDb(partnerRole);
        if (tenantRole != null || viaPartner != null) {
            if (tenantRole == null) {
                return viaPartner;
            }
            return viaPartner != null && viaPartner.ordinal() < tenantRole.ordinal() ? viaPartner : tenantRole;
        }
        return null;
    }

    public void setTenant(UUID tenantId) {
        membership.setTenant(tenantId);
    }
}
