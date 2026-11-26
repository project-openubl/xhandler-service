package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateTenant")
public record UpdateTenantRepresentation(@NotBlank @Size(max = 200) String name) {}
