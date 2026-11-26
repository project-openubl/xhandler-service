package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Public lookups by printed data (DESIGN.md §12), across tenants; every method joins the caller's transaction. */
public interface PublicLookupProvider {

    /** The latest OSE answer to a document: {@code result} ("cdr" or other) and its timestamps. */
    record OseSubmission(String result, Instant cdrIssuedAt, Instant receivedAt) {}

    /** Counts a lookup for {@code key} in the current hour; returns the hour's hits so far. */
    int countLookup(String key);

    /** A production document's status by its printed data (SECURITY DEFINER), or null if not found. */
    String getPublicDocumentStatus(String ruc, String tipoComprobante, String serie, long numero, LocalDate fechaEmision,
            BigDecimal total);

    /** The OSE submission of a document, a CDR first, then the latest. */
    Optional<OseSubmission> getOseSubmission(String issuerRuc, String tipoComprobante, String serie, long numero);
}
