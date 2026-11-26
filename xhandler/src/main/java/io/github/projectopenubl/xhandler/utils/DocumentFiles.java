package io.github.projectopenubl.xhandler.utils;

import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.security.TenantScope;

/** Download names and media types of a document's files (xml, cdr, pdf, json). */
public final class DocumentFiles {

    private DocumentFiles() {}

    public static String contentType(String kind) {
        return switch (kind) {
            case "xml" -> "application/xml";
            case "cdr" -> "application/zip";
            case "pdf" -> "application/pdf";
            default -> "application/json";
        };
    }

    public static String fileName(TenantScope scope, DocumentModel doc, String kind) {
        String base = doc.getTipoComprobante() + "-" + doc.serieNumero();
        return switch (kind) {
            case "xml" -> base + ".xml";
            case "cdr" -> "R-" + base + ".zip";
            case "pdf" -> base + ".pdf";
            default -> base + "-" + kind + ".json";
        };
    }
}
