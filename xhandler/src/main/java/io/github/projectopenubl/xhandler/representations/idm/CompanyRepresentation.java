package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CompanyView")
public record CompanyRepresentation(
        UUID id, String ruc, String razonSocial, String nombreComercial, String sunatEnv,
        String emissionMode, Instant rucVerifiedAt, String email, boolean deliveredOnEmitDefault, boolean sendingPaused,
        String direccion, String pdfFormat, boolean emailOnAccept, boolean hasLogo,
        String libroReclamacionesUrl,
        @Schema(description = "Asunto del correo al comprador; null usa el predeterminado.") String emailSubject,
        @Schema(description = "Cuerpo del correo al comprador; null usa el predeterminado.") String emailBody) {}
