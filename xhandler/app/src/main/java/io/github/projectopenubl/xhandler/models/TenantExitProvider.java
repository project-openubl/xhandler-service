package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A tenant leaving (DESIGN.md §13): its exports, the exit itself and the deletion after the exit
 * period. Every method joins the caller's transaction, which entered the tenant (except the
 * deletion ones, which go through SECURITY DEFINER functions).
 */
public interface TenantExitProvider {

    /** A row of {@code tenant_export}; {@code parts} as stored (JSON text, with storage keys). */
    record ExportRow(UUID id, String status, String parts, Integer documents, String detail, Instant createdAt, Instant doneAt) {}

    /** The {@code parts} column of an export that exists (JSON text, null while not done). */
    record ExportParts(String parts) {}

    /** A document to export; the values other than ids, timestamp and storage keys are as read from the database. */
    record ExportDocumentRow(UUID id, Object ruc, Object sunatEnv, Object tipoComprobante, Object serie, Object numero, Object fechaEmision,
            Object status, Object sunatCode, Object moneda, Object total, Object customerTipo, Object customerNumero, Instant createdAt,
            String xmlRef, String cdrRef) {}

    /** A reception to export; the values other than the id and storage keys are as read from the database. */
    record ExportReceptionRow(UUID id, Object ruc, Object supplierRuc, Object tipoComprobante, Object serie, Object numero, Object fechaEmision,
            Object validityState, String xmlRef, String pdfRef, String cdrRef) {}

    /** Whether an export is pending or was requested in the last hour. */
    boolean hasRecentExport();

    void addExport(UUID id, UUID tenantId, String requestedBy);

    void setExportJob(UUID id, UUID jobId);

    /** Fails pending exports whose job died (failed or canceled); returns how many. */
    int failInterruptedExports();

    /** The 20 most recent exports. */
    List<UUID> getRecentExportIds();

    Optional<ExportRow> getExport(UUID id);

    Optional<ExportParts> getExportParts(UUID id);

    void markExportDone(UUID id, String partsJson, int documents);

    void markExportFailed(UUID id, String detail);

    /** Documents waiting for SUNAT (pending_send, in_progress, unknown, voiding). */
    long countOpenDocuments();

    /** Whether a done export of the last 30 days is newer than every document. */
    boolean hasExportCoveringAllDocuments();

    /** The tenant becomes exited, deleted after {@code deleteAfter} (null: never). */
    void markExited(UUID tenantId, Instant deleteAfter);

    /** A page of documents after ({@code afterCreated}, {@code afterId}), by creation. */
    List<ExportDocumentRow> getExportDocuments(Instant afterCreated, UUID afterId, int limit);

    /** A page of receptions after {@code afterId}, by id. */
    List<ExportReceptionRow> getExportReceptions(UUID afterId, int limit);

    /** Tenants whose exit period is over. */
    List<UUID> getTenantsToDelete();

    /** Deletes every row of the tenant; returns the counts as JSON text. */
    String purgeTenant(UUID tenantId, int filesDeleted);
}
