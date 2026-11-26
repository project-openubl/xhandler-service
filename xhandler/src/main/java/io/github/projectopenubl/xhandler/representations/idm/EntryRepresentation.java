package io.github.projectopenubl.xhandler.representations.idm;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Entry")
public record EntryRepresentation(UUID id, String kind, String key, JsonNode data, Instant createdAt, Instant updatedAt) {}
