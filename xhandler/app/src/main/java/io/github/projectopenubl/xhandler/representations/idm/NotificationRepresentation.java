package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "NotificationView")
public record NotificationRepresentation(UUID id, String kind, String title, String body, UUID documentId, Instant createdAt, Instant readAt) {}
