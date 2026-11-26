package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link SigningKeyModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface SigningKeyProvider {

    /** Creates a signingKey with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted signingKey with {@code id}; see {@link #persist}. */
    SigningKeyModel newSigningKey(UUID id);

    void persist(SigningKeyModel signingKey);

    void delete(SigningKeyModel signingKey);

    SigningKeyModel addSigningKey(UUID id, Consumer<SigningKeyModel> init);

    Optional<SigningKeyModel> getSigningKey(UUID id);

    /** Same as {@link #getSigningKey}, locking the row until the transaction ends. */
    Optional<SigningKeyModel> getSigningKeyForUpdate(UUID id);

    /** Same as {@link #getSigningKey}, share-locking the row until the transaction ends. */
    Optional<SigningKeyModel> getSigningKeyForShare(UUID id);

    /** Re-reads the signingKey from the database (after native updates that bypass the session). */
    void refresh(SigningKeyModel signingKey);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();
    Optional<SigningKeyModel> getActiveSigningKey(UUID companyId);

    /** Every key, not only active ones, locked. */
    List<SigningKeyModel> getSigningKeysForUpdate();
}
