package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "DeliveryView")
public record DeliveryRepresentation(UUID id, UUID eventId, String eventType, String status, int attempts, Instant nextAttemptAt,
        Integer lastStatusCode, String lastError, Instant deliveredAt, Instant createdAt) {}
