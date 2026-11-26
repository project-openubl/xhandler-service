package io.github.projectopenubl.xhandler.representations.idm;

import io.github.projectopenubl.xhandler.managers.NumberingManager;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * {@code nextNumber} only moves forward (numbers are never reused, DESIGN.md §8); {@code active}
 * false deactivates the series, so emits refuse it.
 */
@Schema(name = "UpdateSeries")
public record UpdateSeriesRepresentation(@Min(1) @Max(NumberingManager.MAX_NUMBER) Long nextNumber, Boolean active) {}
