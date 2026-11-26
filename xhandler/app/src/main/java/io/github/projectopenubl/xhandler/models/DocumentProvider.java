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
}
