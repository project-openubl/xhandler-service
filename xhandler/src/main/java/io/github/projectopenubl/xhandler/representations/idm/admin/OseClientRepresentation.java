package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** An OSE user (an issuer or its PSE). */
@Schema(name = "OseClient")
public record OseClientRepresentation(UUID id, String issuerRuc, String username, UUID tenantId, String status, Instant lockedUntil, Instant lastLoginAt) {}
