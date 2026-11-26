package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SessionView")
public record SessionRepresentation(UUID id, String operator, String reason, Instant requestedAt, String approvedBy, Instant expiresAt, Instant revokedAt) {}
