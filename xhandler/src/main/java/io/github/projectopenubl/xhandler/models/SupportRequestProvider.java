package io.github.projectopenubl.xhandler.models;

import java.util.Optional;
import java.util.UUID;

/** Support sessions requested by platform operators (DESIGN.md §26.1). Joins the caller's transaction. */
public interface SupportRequestProvider {

    /** Asks a tenant for a support session (SECURITY DEFINER); the session id, or empty when the tenant is unknown. */
    Optional<UUID> requestSupportSession(UUID tenantId, String operator, String reason);
}
