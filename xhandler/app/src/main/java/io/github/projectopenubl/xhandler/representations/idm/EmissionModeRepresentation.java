package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EmissionMode")
public record EmissionModeRepresentation(@NotBlank @Pattern(regexp = "software|pse") String emissionMode) {}
