package io.github.projectopenubl.xhandler.models;

import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link CredentialModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface CredentialProvider {

    /** Creates a credential with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted credential with {@code id}; see {@link #persist}. */
    CredentialModel newCredential(UUID id);

    void persist(CredentialModel credential);

    void delete(CredentialModel credential);

    CredentialModel addCredential(UUID id, Consumer<CredentialModel> init);

    Optional<CredentialModel> getCredential(UUID id);

    /** Same as {@link #getCredential}, locking the row until the transaction ends. */
    Optional<CredentialModel> getCredentialForUpdate(UUID id);

    /** Same as {@link #getCredential}, share-locking the row until the transaction ends. */
    Optional<CredentialModel> getCredentialForShare(UUID id);

    /** Re-reads the credential from the database (after native updates that bypass the session). */
    void refresh(CredentialModel credential);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    Optional<CredentialModel> getCredential(UUID companyId, String kind);

    List<CredentialModel> getCredentials(UUID companyId);

    /** Secrets sealed with a master key other than {@code masterKeyId}, locked. */
    List<CredentialModel> getCredentialsToRewrapForUpdate(String masterKeyId);
}
