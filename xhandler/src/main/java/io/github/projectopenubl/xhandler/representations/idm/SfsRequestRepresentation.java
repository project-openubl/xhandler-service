package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SfsRequest")
public record SfsRequestRepresentation(@NotBlank String fileName, @NotBlank String cab, @NotBlank String det) {}
