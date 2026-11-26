package io.github.projectopenubl.xhandler.representations.idm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PublicDocument")
public record PublicDocumentRepresentation(
        String emisorRuc,
        String emisorRazonSocial,
        String tipoComprobante,
        String serieNumero,
        LocalDate fechaEmision,
        String moneda,
        BigDecimal total,
        String status,
        List<String> files,
        @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "El Libro de Reclamaciones virtual del emisor, si lo configuró.")
        String libroReclamacionesUrl) {}
