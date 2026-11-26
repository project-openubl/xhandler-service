package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A period in a list: its state, without the reconciliation (read it with {@code getSirePeriod}). */
@Schema(name = "SirePeriodSummary")
public record SirePeriodSummaryRepresentation(
        UUID id, String book, String period, String sunatEnv, String status, String detail, boolean proposalFile,
        boolean replacementFile, Instant createdAt, Instant updatedAt) {}
