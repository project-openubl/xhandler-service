package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AuthorizationView")
public record AuthorizationRepresentation(UUID id, LocalDate authorizedAt, boolean evidence, String registeredBy, Instant approvedAt, Instant revokedAt) {}
