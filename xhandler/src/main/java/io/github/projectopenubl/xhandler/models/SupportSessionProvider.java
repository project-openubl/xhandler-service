package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link SupportSessionModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface SupportSessionProvider {

    /** Creates a supportSession with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted supportSession with {@code id}; see {@link #persist}. */
    SupportSessionModel newSupportSession(UUID id);

    void persist(SupportSessionModel supportSession);

    void delete(SupportSessionModel supportSession);

    SupportSessionModel addSupportSession(UUID id, Consumer<SupportSessionModel> init);

    Optional<SupportSessionModel> getSupportSession(UUID id);

    /** Same as {@link #getSupportSession}, locking the row until the transaction ends. */
    Optional<SupportSessionModel> getSupportSessionForUpdate(UUID id);

    /** Same as {@link #getSupportSession}, share-locking the row until the transaction ends. */
    Optional<SupportSessionModel> getSupportSessionForShare(UUID id);

    /** Re-reads the supportSession from the database (after native updates that bypass the session). */
    void refresh(SupportSessionModel supportSession);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    List<SupportSessionModel> getSupportSessions();
}
