package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A part to download by its position in {@code parts}. Its storage key stays internal: it is
 * read only to serve the download.
 */
@Schema(name = "ExportPart")
public record ExportPartRepresentation(
        @Schema(required = true, description = "Nombre del ZIP.") String name,
        @Schema(required = true, description = "Tamaño en bytes.") long size,
        @Schema(description = "Comprobantes (o recepciones) en la parte; en el índice, todos los comprobantes.") Integer documents) {}
