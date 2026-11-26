package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Association")
public record AssociationRepresentation(@NotBlank @Pattern(regexp = "\\d{11}") String ruc, @NotNull LocalDate startsOn, LocalDate endsOn) {}
