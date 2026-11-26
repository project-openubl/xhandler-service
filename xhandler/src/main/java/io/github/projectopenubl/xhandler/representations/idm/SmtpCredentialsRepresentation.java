package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The tenant's own SMTP server for document emails (DESIGN.md §11.6). */
@Schema(name = "SmtpCredentials")
public record SmtpCredentialsRepresentation(@NotBlank @Size(max = 255) String host, @jakarta.validation.constraints.Min(1)
        @jakarta.validation.constraints.Max(65535) int port, @NotBlank @Size(max = 200) String username,
        @NotBlank @Size(max = 300) String password, @NotBlank @jakarta.validation.constraints.Email String from, Boolean startTls) {}
