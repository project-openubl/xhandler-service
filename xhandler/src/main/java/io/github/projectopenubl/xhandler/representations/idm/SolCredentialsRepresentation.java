package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** SUNAT SOL user (a secondary user with the CPE profile). */
@Schema(name = "SolCredentials")
public record SolCredentialsRepresentation(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}
