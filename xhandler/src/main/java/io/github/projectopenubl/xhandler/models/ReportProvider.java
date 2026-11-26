package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Register reports (DESIGN.md §11.9) and the rows they're built from; every method joins the
 * caller's transaction, which entered the tenant.
 */
public interface ReportProvider {

    record ReportRow(UUID id, String register, String format, String period, String sunatEnv, String status, String fileName, Integer rows,
            String detail, UUID jobId, Instant createdAt, Instant doneAt) {}

    /** What a report job needs to know about its report. */
    record ReportRequestRow(UUID companyId, String sunatEnv, String register, String format, String period) {}

    record ReportFile(String fileRef, String fileName, String format) {}

    /** A document of the sales register; {@code amounts} is JSON text, {@code affected*} the document a note modifies. */
    record SalesRow(LocalDate fecha, String tipo, String serie, long numero, String customerTipo, String customerNumero, String customerNombre,
            String moneda, BigDecimal tipoCambio, String amounts, BigDecimal total, String status, LocalDate affectedFecha, String affectedTipo,
            String affectedSerie, Long affectedNumero) {}

    /** A reception of the purchases register; {@code amounts} is JSON text. */
    record PurchaseRow(LocalDate fecha, String tipo, String serie, long numero, String supplierRuc, String supplierName, String moneda,
            String amounts, BigDecimal total, String affectedSerieNumero, String affectedTipo, BigDecimal tipoCambio) {}

    void addReport(UUID id, UUID tenantId, UUID companyId, String sunatEnv, String register, String format, String period, String requestedBy);

    void setReportJob(UUID id, UUID jobId);

    /** Ids of the company's reports, newest first. */
    List<UUID> getReportIds(UUID companyId, int limit, int offset);

    Optional<ReportRow> getReport(UUID id);

    Optional<UUID> getReportCompanyId(UUID id);

    /** The report's file of the company; empty when the report doesn't exist (its {@code fileRef} is null until it's done). */
    Optional<ReportFile> getReportFile(UUID id, UUID companyId);

    /** Throws {@code NoResultException} when the report doesn't exist. */
    ReportRequestRow getReportRequest(UUID id);

    /** Records the outcome of a report: status, file, rows and detail. */
    void finishReport(UUID id, String status, String fileRef, String fileName, Integer rows, String detail);

    /** Accepted (or voided) facturas, boletas and notes of the company issued between {@code from} and {@code to}, in register order. */
    List<SalesRow> getSalesRows(UUID companyId, String sunatEnv, LocalDate from, LocalDate to);

    /** Receptions SUNAT didn't report as voided, rejected or nonexistent, issued between {@code from} and {@code to}, in register order. */
    List<PurchaseRow> getPurchaseRows(UUID companyId, LocalDate from, LocalDate to);

    /** Documents issued between {@code from} and {@code to} without a final answer yet (or stuck in exception). */
    long countUnresolvedDocuments(UUID companyId, String sunatEnv, LocalDate from, LocalDate to);
}
