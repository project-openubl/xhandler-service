package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Entry")
public record SireCalendarEntryRepresentation(@NotNull @Pattern(regexp = "\\d{4}(0[1-9]|1[0-2])") String period, @Min(0) @Max(9) int rucLastDigit,
        @NotNull LocalDate dueDate) {}
