package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A commercial RUC/DNI lookup provider (DESIGN.md §17): URL with {tipo}/{numero}, and the tenant's token. */
@Schema(name = "LookupCredentials")
public record LookupCredentialsRepresentation(@NotBlank @Size(max = 2000) String url, @Size(max = 500) String token) {}
