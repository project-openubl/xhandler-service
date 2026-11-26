package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Authorization")
public record AuthorizationRequestRepresentation(@NotNull LocalDate authorizedAt, @NotBlank String evidenceBase64) {}
