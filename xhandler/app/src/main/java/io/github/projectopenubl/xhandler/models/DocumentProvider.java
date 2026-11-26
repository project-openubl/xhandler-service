package io.github.projectopenubl.xhandler.models;

import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link DocumentModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface DocumentProvider {

    /** Creates a document with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted document with {@code id}; see {@link #persist}. */
    DocumentModel newDocument(UUID id);

    void persist(DocumentModel document);

    void delete(DocumentModel document);

    DocumentModel addDocument(UUID id, Consumer<DocumentModel> init);

    Optional<DocumentModel> getDocument(UUID id);

    /** Same as {@link #getDocument}, locking the row until the transaction ends. */
    Optional<DocumentModel> getDocumentForUpdate(UUID id);

    /** Same as {@link #getDocument}, share-locking the row until the transaction ends. */
    Optional<DocumentModel> getDocumentForShare(UUID id);

    /** Re-reads the document from the database (after native updates that bypass the session). */
    void refresh(DocumentModel document);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    List<UUID> getReversalIds(UUID companyId, String sunatEnv, String serie);

    List<DocumentModel> getReversedDocuments(List<UUID> reversalIds, String tipoComprobante, LocalDate fechaEmision);

    /** A document the company issued (not an OSE-inbound one) with that number. */
    Optional<DocumentModel> getIssuedDocument(UUID companyId, String sunatEnv, String tipoComprobante, String serie, long numero);

    List<DocumentModel> getDocumentsByNumber(UUID companyId, String sunatEnv, String tipoComprobante, String serie, long numero);

    /** Documents not in a summary, in one of {@code statuses}, locked; the other filters are optional. */
    List<DocumentModel> getDocumentsToRetryForUpdate(Set<String> statuses, UUID companyId, LocalDate from, LocalDate to);

    Optional<DocumentModel> getDocumentByLabel(UUID companyId, String label, String value);

    List<DocumentModel> getSummaryLines(UUID summaryId);

    List<DocumentModel> getSummaryLinesForUpdate(UUID summaryId);

    List<DocumentModel> getVoidedBy(UUID voidingId);

    List<DocumentModel> getVoidingOriginalsForUpdate(UUID voidingId);

    /**
     * Lines a resumen diario may take, locked: SUNAT can't have them and no attempt is in flight
     * (DESIGN.md §9.2.4). {@code skipLocked} skips rows other transactions hold.
     */
    List<DocumentModel> getSummaryCandidatesForUpdate(UUID companyId, String sunatEnv, LocalDate fecha, LocalDate cutoff, int max,
            boolean skipLocked);

    /** Expires the document's open access links; returns how many. */
    int revokeAccessLinks(UUID documentId);

    List<DocumentModel> searchDocuments(DocumentSearch search, int offset, int limit);

    /** A row of a cross-tenant SECURITY DEFINER listing: the tenant and the row's id. */
    record TenantRow(UUID tenantId, UUID id) {}

    /** A company, environment and issue day whose boletas need a resumen diario. */
    record SummaryGroup(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha) {}

    /** Wakes the reconciler ({@code pg_notify('xhandler_reconcile')}), on commit. */
    void notifyReconcile();

    /**
     * Moves an accepted, undelivered, not yet voided document to {@code voiding}; returns how many
     * rows changed (0 if it changed meanwhile).
     */
    int markVoiding(UUID documentId, UUID voidingId, String motivo, java.time.Instant reversalCommunicatedAt);

    /** Records a buyer portal download of the document. */
    void addPortalDelivery(UUID id, UUID tenantId, UUID documentId);

    /** Records a call to a SUNAT endpoint, for its health. */
    void addSunatCall(String endpoint, boolean healthy, java.time.Instant startedAt);

    /** Groups of boletas needing a resumen diario, across tenants (SECURITY DEFINER). */
    List<SummaryGroup> getBoletasNeedingSummary(LocalDate today);

    /** Whether a job holding the document's resource is waiting, running or canceling. */
    boolean hasActiveJob(UUID documentId);

    /** Documents whose legal deadline falls within {@code horizon}, across tenants (SECURITY DEFINER). */
    List<TenantRow> getDocumentsAtRisk(java.time.Duration horizon);

    /** Up to 200 documents without amounts, across tenants (SECURITY DEFINER). */
    List<TenantRow> getDocumentsWithoutAmounts();

    /** Up to 200 receptions without amounts, across tenants (SECURITY DEFINER). */
    List<TenantRow> getReceptionsWithoutAmounts();

    /** Marks a document's (or reception's) amounts as unreadable, if not recorded yet. */
    void markAmountsUnreadable(UUID id, boolean reception);

    /** The storage key of the document's signed XML. */
    Optional<String> getSignedXmlStorageKey(UUID documentId);

    /** The storage key of a reception's XML. */
    Optional<String> getReceptionXmlRef(UUID receptionId);

    /** Records a document's amounts (and default payment terms of a factura), if not recorded yet. */
    void setDocumentAmounts(UUID documentId, String amountsJson, String paymentTerms);

    /** Records a reception's amounts, if not recorded yet. */
    void setReceptionAmounts(UUID receptionId, String amountsJson);
}
