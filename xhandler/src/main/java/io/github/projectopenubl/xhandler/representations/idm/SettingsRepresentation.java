package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Settings")
public record SettingsRepresentation(
        @Size(max = 300) String nombreComercial,
        @Email @Size(max = 300) String email,
        Boolean deliveredOnEmitDefault,
        @Size(max = 500) String direccion,
        @Pattern(regexp = "a4|ticket80|ticket58") String pdfFormat,
        Map<String, Object> pdfOptions,
        Boolean emailOnAccept,
        @Size(max = 200) @Pattern(regexp = "[^\\r\\n]*", message = "debe ser una sola línea") String emailSubject,
        @Size(max = 5000) String emailBody,
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Enlace https al Libro de Reclamaciones virtual de la empresa, que el portal del comprador muestra; vacío lo quita.")
        @Size(max = 500) String libroReclamacionesUrl) {}
