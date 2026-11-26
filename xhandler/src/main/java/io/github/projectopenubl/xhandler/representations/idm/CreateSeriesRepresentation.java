package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateSeries")
public record CreateSeriesRepresentation(
        @NotBlank @Pattern(regexp = "\\d{2}") String tipoComprobante,
        @NotBlank @Pattern(regexp = "[A-Z0-9]{4}", message = "debe tener 4 caracteres en mayúsculas o dígitos") String serie,
        @Min(1) Long nextNumber) {}
