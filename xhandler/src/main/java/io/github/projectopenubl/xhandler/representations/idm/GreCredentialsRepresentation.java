package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** GRE REST API (DESIGN.md §3.1): the SOL user plus the API client created in SOL. */
@Schema(name = "GreCredentials")
public record GreCredentialsRepresentation(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password,
        @NotBlank @Size(max = 200) String clientId, @NotBlank @Size(max = 300) String clientSecret) {}
