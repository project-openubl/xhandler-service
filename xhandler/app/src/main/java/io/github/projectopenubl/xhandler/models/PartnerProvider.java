package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Partners (DESIGN.md §1.2, §14), through their SECURITY DEFINER functions. Joins the caller's transaction. */
public interface PartnerProvider {

    record PartnerMembership(UUID id, String name, String role) {}

    record PartnerTenantUsage(UUID id, String name, String status, String plan, long productionDocuments, long otherDocuments) {}

    /** The partner's branding as JSON text (null when never set), and whether it has a logo. */
    record PartnerBranding(String brandingJson, boolean hasLogo) {}

    /** The partners {@code kind:subject} is a member of, with its role. */
    List<PartnerMembership> getPartnersOf(String kind, String subject);

    /** Creates a tenant of the partner; {@code ownerKind}/{@code ownerSubject} may be null (no owner). */
    void createPartnerTenant(UUID partnerId, UUID id, String name, String by, String ownerKind, String ownerSubject);

    /** The partner's tenants with their documents in {@code [from, to)}. */
    List<PartnerTenantUsage> getPartnerTenants(UUID partnerId, Instant from, Instant to);

    void setPartnerBranding(UUID partnerId, String brandingJson);

    void setPartnerLogo(UUID partnerId, String ref);

    void clearPartnerLogo(UUID partnerId);

    PartnerBranding getPartnerBranding(UUID partnerId);
}
