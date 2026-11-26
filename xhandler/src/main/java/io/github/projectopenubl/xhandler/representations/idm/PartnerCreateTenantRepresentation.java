package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreatePartnerTenant")
public record PartnerCreateTenantRepresentation(@NotBlank @Size(max = 200) String name, @Pattern(regexp = "user|client") String ownerKind,
        @Size(max = 200) String ownerSubject) {}
