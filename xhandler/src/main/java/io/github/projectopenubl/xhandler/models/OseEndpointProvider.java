package io.github.projectopenubl.xhandler.models;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link OseEndpointModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface OseEndpointProvider {

    /** Creates a oseEndpoint with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted oseEndpoint with {@code id}; see {@link #persist}. */
    OseEndpointModel newOseEndpoint(UUID id);

    void persist(OseEndpointModel oseEndpoint);

    void delete(OseEndpointModel oseEndpoint);

    OseEndpointModel addOseEndpoint(UUID id, Consumer<OseEndpointModel> init);

    Optional<OseEndpointModel> getOseEndpoint(UUID id);

    /** Same as {@link #getOseEndpoint}, locking the row until the transaction ends. */
    Optional<OseEndpointModel> getOseEndpointForUpdate(UUID id);

    /** Same as {@link #getOseEndpoint}, share-locking the row until the transaction ends. */
    Optional<OseEndpointModel> getOseEndpointForShare(UUID id);

    /** Re-reads the oseEndpoint from the database (after native updates that bypass the session). */
    void refresh(OseEndpointModel oseEndpoint);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();
    Optional<OseEndpointModel> getOseEndpointByCompany(UUID companyId);
}
