package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RequestSession")
public record RequestSessionRepresentation(@NotNull UUID tenantId, @NotBlank @Size(max = 500) String reason) {}
