package io.github.projectopenubl.xhandler.models;

import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link DocumentDraftModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface DocumentDraftProvider {

    /** Creates a documentDraft with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted documentDraft with {@code id}; see {@link #persist}. */
    DocumentDraftModel newDocumentDraft(UUID id);

    void persist(DocumentDraftModel documentDraft);

    void delete(DocumentDraftModel documentDraft);

    DocumentDraftModel addDocumentDraft(UUID id, Consumer<DocumentDraftModel> init);

    Optional<DocumentDraftModel> getDocumentDraft(UUID id);

    /** Same as {@link #getDocumentDraft}, locking the row until the transaction ends. */
    Optional<DocumentDraftModel> getDocumentDraftForUpdate(UUID id);

    /** Same as {@link #getDocumentDraft}, share-locking the row until the transaction ends. */
    Optional<DocumentDraftModel> getDocumentDraftForShare(UUID id);

    /** Re-reads the documentDraft from the database (after native updates that bypass the session). */
    void refresh(DocumentDraftModel documentDraft);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    /** Drafts not deleted, newest first; {@code companyId} and {@code status} are optional filters. */
    List<DocumentDraftModel> getDocumentDrafts(UUID companyId, String status, int offset, int limit);
}
