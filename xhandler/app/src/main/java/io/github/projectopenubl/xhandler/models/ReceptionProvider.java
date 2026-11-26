package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Supplier documents received by a company (DESIGN.md §11.5); every method joins the caller's
 * transaction, which entered the tenant (except {@link #getReceptionsToCheck}, cross-tenant).
 */
public interface ReceptionProvider {

    /** A reception to store; {@code amounts} is JSON text (or null). */
    record NewReception(UUID id, UUID tenantId, UUID companyId, String supplierRuc, String tipoComprobante, String serie, Long numero,
            LocalDate fechaEmision, String moneda, BigDecimal total, String digestValue, String xmlRef, String pdfRef, String cdrRef,
            String amounts, String affectedSerieNumero, String affectedTipo) {}

    /** A reception as the API shows it. */
    record ReceptionRow(UUID id, String supplierRuc, String tipoComprobante, String serieNumero, LocalDate fechaEmision, String moneda,
            BigDecimal total, String validityState, Instant validityCheckedAt, boolean hasPdf, boolean hasCdr, Instant createdAt) {}

    /** What SUNAT's validity service needs about a reception. */
    record ValidityRow(UUID companyId, String supplierRuc, String tipoComprobante, String serie, Long numero, LocalDate fechaEmision,
            BigDecimal total) {}

    /** A reception due for a validity check, with its tenant. */
    record DueReception(UUID tenantId, UUID receptionId) {}

    /** Inserts the reception and flushes, so a duplicate surfaces here. */
    void addReception(NewReception reception);

    /** Ids of the company's receptions, newest first; {@code from} and {@code to} are optional. */
    List<UUID> getReceptionIds(UUID companyId, LocalDate from, LocalDate to, int limit, int offset);

    boolean existsReception(UUID id, UUID companyId);

    Optional<ReceptionRow> getReception(UUID id);

    /** The stored file reference of a reception of the company; {@code kind} is xml, pdf or cdr. Empty when absent. */
    Optional<String> getFileRef(UUID id, UUID companyId, String kind);

    /** Receptions due for a validity check, across tenants (SECURITY DEFINER {@code receptions_to_check}). */
    List<DueReception> getReceptionsToCheck();

    /** The reception's validity inputs; throws {@code NoResultException} when it doesn't exist. */
    ValidityRow getValidityRow(UUID id);

    /** Records a validity check; a null state keeps the current one. */
    void stampValidity(UUID id, String state);

    record ReceptionRef(UUID tenantId, UUID id) {}

    /** Up to 200 receptions without amounts, across tenants (SECURITY DEFINER). */
    List<ReceptionRef> getReceptionsWithoutAmounts();

    /** The storage key of a reception's XML. */
    Optional<String> getReceptionXmlRef(UUID receptionId);

    /** Records a reception's amounts, if not recorded yet. */
    void setReceptionAmounts(UUID receptionId, String amountsJson);

    /** Marks a reception's amounts as unreadable, if not recorded yet. */
    void markReceptionAmountsUnreadable(UUID receptionId);
}
