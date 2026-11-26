package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "NewRequest")
public record NewRequestRepresentation(
        @NotBlank @Pattern(regexp = "[0-9A-Z]{1,2}") String subjectDocType,
        @NotBlank @Size(max = 20) String subjectDocNumber,
        @Size(max = 200) String subjectName,
        @NotBlank @Pattern(regexp = "access|rectification|cancellation|opposition") String kind,
        @Size(max = 2000) String details) {}
