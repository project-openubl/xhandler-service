package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SUNAT's REST APIs for SIRE and factura negociable conformity (DESIGN.md §3.3, §11.7): the API
 * client created in SOL plus a SOL user with the SIRE profile (RUC + user).
 */
@Schema(name = "SireCredentials")
public record SireCredentialsRepresentation(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password,
        @NotBlank @Size(max = 200) String clientId, @NotBlank @Size(max = 300) String clientSecret) {}
