package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Blocked")
public record BlockedRepresentation(@NotBlank @Pattern(regexp = "\\d{11}") String ruc, LocalDate listedOn) {}
