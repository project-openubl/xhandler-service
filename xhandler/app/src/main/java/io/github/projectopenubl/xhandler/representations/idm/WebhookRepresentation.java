package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "WebhookView")
public record WebhookRepresentation(UUID id, String url, List<String> events, boolean active, Instant createdAt, String secret) {}
