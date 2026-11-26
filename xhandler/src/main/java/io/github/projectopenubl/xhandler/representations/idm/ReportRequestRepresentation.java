package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ReportRequest")
public record ReportRequestRepresentation(
        @NotBlank @Pattern(regexp = "sales|purchases") String register,
        @NotBlank @Pattern(regexp = "csv|xlsx|ple|sire") String format,
        @NotBlank @Pattern(regexp = "\\d{4}(0[1-9]|1[0-2])") String period) {}
