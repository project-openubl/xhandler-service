package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateMember")
public record UpdateMemberRepresentation(@NotBlank @Pattern(regexp = "admin|operator|viewer") String role) {}
