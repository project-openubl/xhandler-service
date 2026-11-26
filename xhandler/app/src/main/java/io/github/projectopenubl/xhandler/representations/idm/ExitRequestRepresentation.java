package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ExitRequest")
public record ExitRequestRepresentation(@NotBlank String confirmName) {}
