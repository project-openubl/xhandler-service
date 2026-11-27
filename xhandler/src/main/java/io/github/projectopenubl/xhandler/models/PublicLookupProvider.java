package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public lookups by printed data (DESIGN.md §12), across tenants; every method joins the caller's transaction. */
public interface PublicLookupProvider {

    /** A production document's status by its printed data (SECURITY DEFINER), or null if not found. */
    String getPublicDocumentStatus(String ruc, String tipoComprobante, String serie, long numero, LocalDate fechaEmision,
            BigDecimal total);
}
