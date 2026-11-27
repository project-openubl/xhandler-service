package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "LookupRequest")
public record LookupRequestRepresentation(
        @NotBlank @Pattern(regexp = "\\d{11}") String ruc,
        @NotBlank @Pattern(regexp = "\\d{2}") String tipoComprobante,
        @NotBlank @Pattern(regexp = "[A-Z0-9]{4}") String serie,
        @NotNull Long numero,
        @NotNull LocalDate fechaEmision,
        @NotNull BigDecimal total) {}
