package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Factura negociable conformity (DESIGN.md §11.8); every method joins the caller's transaction,
 * which entered the tenant (except {@link #getConformitiesToCheck}, cross-tenant).
 */
public interface ConformityProvider {

    /** A conformity with its factura's fields. */
    record ConformityRow(UUID documentId, String serie, Long numero, String buyerRuc, String state, Instant acceptedAt, Instant windowEndsAt,
            Instant checkedAt, String checkDetail, Instant resolvedAt, BigDecimal total, String moneda, LocalDate fechaEmision,
            String customerNombre) {}

    /** What the poller needs about a conformity. */
    record ConformityCheck(UUID documentId, Instant windowEndsAt, Instant remindedAt) {}

    /** A conformity due for a check, with its tenant. */
    record DueConformity(UUID tenantId, UUID conformityId) {}

    Optional<ConformityRow> getDocumentConformity(UUID documentId);

    /** The company's conformities, newest acceptance first; {@code state} (optional) filters. */
    List<ConformityRow> getConformities(UUID companyId, String state, int limit, int offset);

    /** Follows a factura accepted at {@code acceptedAt}; does nothing when it's already followed. */
    void addConformity(UUID id, UUID tenantId, UUID companyId, UUID documentId, String buyerRuc, Instant acceptedAt, Instant windowEndsAt);

    /** Closes the document's pending conformity (the factura was voided). */
    void closeConformity(UUID documentId);

    /** Conformities due for a check, across tenants (SECURITY DEFINER {@code conformities_to_check}). */
    List<DueConformity> getConformitiesToCheck();

    /** Throws {@code NoResultException} when the conformity doesn't exist. */
    ConformityCheck getConformityCheck(UUID id);

    /** Records SUNAT's final answer on a pending conformity; returns how many rows changed (0 when not pending). */
    int resolveConformity(UUID id, String state);

    /** Marks the reminder sent; returns how many rows changed (0 when it already was). */
    int markReminded(UUID id);

    /** Records a check that gave no final answer. */
    void stampConformity(UUID id, String detail);
}
