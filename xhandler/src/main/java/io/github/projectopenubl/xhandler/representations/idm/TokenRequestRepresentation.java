package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TokenRequest")
public record TokenRequestRepresentation(@NotBlank @Size(max = 100) String label) {}
