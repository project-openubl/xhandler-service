package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link DocumentEventModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface DocumentEventProvider {

    /** Creates a documentEvent with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted documentEvent with {@code id}; see {@link #persist}. */
    DocumentEventModel newDocumentEvent(UUID id);

    void persist(DocumentEventModel documentEvent);

    void delete(DocumentEventModel documentEvent);

    DocumentEventModel addDocumentEvent(UUID id, Consumer<DocumentEventModel> init);

    Optional<DocumentEventModel> getDocumentEvent(UUID id);

    /** Same as {@link #getDocumentEvent}, locking the row until the transaction ends. */
    Optional<DocumentEventModel> getDocumentEventForUpdate(UUID id);

    /** Same as {@link #getDocumentEvent}, share-locking the row until the transaction ends. */
    Optional<DocumentEventModel> getDocumentEventForShare(UUID id);

    /** Re-reads the documentEvent from the database (after native updates that bypass the session). */
    void refresh(DocumentEventModel documentEvent);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    /** Records the document's current status in its history. */
    DocumentEventModel addDocumentEvent(DocumentModel document, Integer code, String message, String actor, String correlationId);

    List<DocumentEventModel> getDocumentEvents(UUID documentId);
}
