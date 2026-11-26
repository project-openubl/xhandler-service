package io.github.projectopenubl.xhandler.models;

import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link DocumentFileModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface DocumentFileProvider {

    /** Creates a documentFile with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted documentFile with {@code id}; see {@link #persist}. */
    DocumentFileModel newDocumentFile(UUID id);

    void persist(DocumentFileModel documentFile);

    void delete(DocumentFileModel documentFile);

    DocumentFileModel addDocumentFile(UUID id, Consumer<DocumentFileModel> init);

    Optional<DocumentFileModel> getDocumentFile(UUID id);

    /** Same as {@link #getDocumentFile}, locking the row until the transaction ends. */
    Optional<DocumentFileModel> getDocumentFileForUpdate(UUID id);

    /** Same as {@link #getDocumentFile}, share-locking the row until the transaction ends. */
    Optional<DocumentFileModel> getDocumentFileForShare(UUID id);

    /** Re-reads the documentFile from the database (after native updates that bypass the session). */
    void refresh(DocumentFileModel documentFile);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();
    Optional<DocumentFileModel> getDocumentFile(UUID documentId, String kind);

    /** Records a stored file of a document. */
    DocumentFileModel addDocumentFile(DocumentModel document, String kind, io.github.projectopenubl.xhandler.files.FileStore.Stored stored);

    List<DocumentFileModel> getDocumentFiles(UUID documentId);
}
