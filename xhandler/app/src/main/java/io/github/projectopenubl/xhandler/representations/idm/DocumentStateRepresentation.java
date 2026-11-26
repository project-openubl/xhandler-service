package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "DocumentState")
public record DocumentStateRepresentation(UUID id, UUID tenantId, String serieNumero, String status, String holdReason, Instant nextAttemptAt,
        int attempts) {}
