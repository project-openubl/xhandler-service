package io.github.projectopenubl.xhandler.representations.idm;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SirePeriodView")
public record SirePeriodRepresentation(
        UUID id, String book, String period, String sunatEnv, String status, String detail, JsonNode reconciliation,
        boolean proposalFile, boolean replacementFile, Instant createdAt, Instant updatedAt) {}
