package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who belongs to which tenant, through the SECURITY DEFINER functions (they cross tenants), and
 * opening a tenant for row-level security (DESIGN.md §6). Every method joins the caller's transaction.
 */
public interface MembershipProvider {

    /** A tenant the caller belongs to, with the caller's role in it. */
    record MemberTenant(UUID id, String name, String role) {}

    /** The caller's role in the tenant as a member ({@code xhandler.member_role}); empty if none. */
    Optional<String> getMemberRole(UUID tenantId, String kind, String subject);

    /** The caller's role in the tenant through its partner ({@code xhandler.partner_role}); empty if none. */
    Optional<String> getPartnerRole(UUID tenantId, String kind, String subject);


    /** {@code set_config('app.tenant_id', tenantId, true)}: row-level security shows that tenant until the transaction ends. */
    void setTenant(UUID tenantId);

    /** The tenants of the caller ({@code xhandler.tenants_of}). */
    List<MemberTenant> getTenantsOf(String kind, String subject);
}
