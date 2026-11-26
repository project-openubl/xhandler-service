package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "NewClient")
public record NewClientRepresentation(@NotBlank @Pattern(regexp = "\\d{11}") String issuerRuc, @Pattern(regexp = "[A-Za-z0-9._-]{4,60}") String username,
        UUID tenantId) {}
