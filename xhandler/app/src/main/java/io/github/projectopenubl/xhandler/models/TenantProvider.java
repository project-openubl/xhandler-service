package io.github.projectopenubl.xhandler.models;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link TenantModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface TenantProvider {

    /** Creates a tenant with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted tenant with {@code id}; see {@link #persist}. */
    TenantModel newTenant(UUID id);

    void persist(TenantModel tenant);

    void delete(TenantModel tenant);

    TenantModel addTenant(UUID id, Consumer<TenantModel> init);

    Optional<TenantModel> getTenant(UUID id);

    /** Same as {@link #getTenant}, locking the row until the transaction ends. */
    Optional<TenantModel> getTenantForUpdate(UUID id);

    /** Same as {@link #getTenant}, share-locking the row until the transaction ends. */
    Optional<TenantModel> getTenantForShare(UUID id);

    /** Re-reads the tenant from the database (after native updates that bypass the session). */
    void refresh(TenantModel tenant);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();
}
