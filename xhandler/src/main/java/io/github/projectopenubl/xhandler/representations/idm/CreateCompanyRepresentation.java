package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateCompany")
public record CreateCompanyRepresentation(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener 11 dígitos") String ruc,
        @NotBlank @Size(max = 300) String razonSocial,
        @Size(max = 300) String nombreComercial) {}
