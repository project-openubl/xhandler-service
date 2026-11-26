package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A document issued to the person (status and amounts, no files). Values as text, as stored. */
@Schema(name = "PersonalDocument")
public record PersonalDocumentRepresentation(UUID id, String issuerRuc, String tipoComprobante, String serieNumero, String fechaEmision, String nombre,
        String moneda, String total, String status) {}
