package io.github.projectopenubl.xhandler.models;

import java.util.UUID;

/**
 * Tenant lifecycle flags platform admins set (DESIGN.md §13): tax-audit hold, archive plan and
 * exit. Every method joins the caller's transaction, which entered the tenant.
 */
public interface PrivacyProvider {

    /** Sets the tenant's tax-audit hold; returns the rows changed (0: unknown or deleted tenant). */
    int setAuditHold(UUID tenantId, boolean value);

    /** Sets the tenant's archive plan; returns the rows changed (0: unknown or deleted tenant). */
    int setArchivePlan(UUID tenantId, boolean value);

    /** An exited tenant without deletion date (it had the archive plan) gets one: the 2-year limit, at least 30 days away. */
    void scheduleExitedTenantDeletion(UUID tenantId);

    /** Brings an exited, not yet deleted tenant back to active; returns the rows changed. */
    int cancelExit(UUID tenantId);
}
