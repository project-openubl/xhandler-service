package io.github.projectopenubl.xhandler.models;

import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link AuditLogModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface AuditLogProvider {

    /** Creates a auditLog with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted auditLog with {@code id}; see {@link #persist}. */
    AuditLogModel newAuditLog(UUID id);

    void persist(AuditLogModel auditLog);

    void delete(AuditLogModel auditLog);

    AuditLogModel addAuditLog(UUID id, Consumer<AuditLogModel> init);

    Optional<AuditLogModel> getAuditLog(UUID id);

    /** Same as {@link #getAuditLog}, locking the row until the transaction ends. */
    Optional<AuditLogModel> getAuditLogForUpdate(UUID id);

    /** Same as {@link #getAuditLog}, share-locking the row until the transaction ends. */
    Optional<AuditLogModel> getAuditLogForShare(UUID id);

    /** Re-reads the auditLog from the database (after native updates that bypass the session). */
    void refresh(AuditLogModel auditLog);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    /** Newest first. */
    List<AuditLogModel> getAuditLogs(int offset, int limit);

    long countAuditLogs(String action, String objectId);
}
