package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Ose")
public record OseRepresentation(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 2000) String invoiceUrl,
        @Size(max = 2000) String perceptionUrl, @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}
