package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AddMember")
public record AddMemberRepresentation(
        @NotBlank @Pattern(regexp = "user|client") String kind,
        @NotBlank String subject,
        @NotBlank @Pattern(regexp = "admin|operator|viewer") String role) {}
