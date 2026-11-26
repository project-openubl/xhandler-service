package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The platform's own credentials and keys (DESIGN.md §6, §7.1), outside any tenant; every method
 * joins the caller's transaction (except {@link #getActivePlatformKey}, which also runs without one).
 */
public interface PlatformSecretProvider {

    /** A platform credential without its secret. */
    record PlatformCredentialRow(UUID id, String purpose, String label, boolean active) {}

    /** A platform credential's sealed secret. */
    record SealedPlatformCredential(UUID id, byte[] ciphertext, byte[] wrappedDataKey, String masterKeyId) {}

    /** A platform key: a vault reference plus its certificate (DER). */
    record PlatformKeyRow(UUID id, String purpose, String keyRef, byte[] certificate, String subject, String ruc, Instant validUntil) {}

    record PlatformKeyRef(UUID id, String keyRef) {}

    void addPlatformCredential(UUID id, String purpose, String label, byte[] ciphertext, byte[] wrappedDataKey, String masterKeyId);

    /** Ordered by label. */
    List<PlatformCredentialRow> getPlatformCredentials(String purpose);

    /** False when the credential doesn't exist. */
    boolean setPlatformCredentialActive(UUID id, boolean active);

    List<UUID> getActivePlatformCredentialIds(String purpose);

    boolean existsPlatformCredential(UUID id, String purpose);

    /**
     * The credential {@code id} when it is active, else a random active one of the purpose; empty
     * when the purpose has no active credential.
     */
    Optional<SealedPlatformCredential> getActivePlatformCredential(UUID id, String purpose);

    /** Credentials sealed with another master key than {@code masterKeyId}, locked. */
    List<SealedPlatformCredential> getPlatformCredentialsToRewrapForUpdate(String masterKeyId);

    void setPlatformCredentialWrappedKey(UUID id, byte[] wrappedDataKey, String masterKeyId);

    /** Every platform key's reference, locked. */
    List<PlatformKeyRef> getPlatformKeyRefsForUpdate();

    /** {@code xhandler.rewrap_platform_key} (the app role can't update {@code key_ref}). */
    void rewrapPlatformKey(UUID id, String keyRef);

    /** {@code xhandler.all_tenant_ids()}. */
    List<UUID> getAllTenantIds();

    /** Marks the active key of the purpose replaced. */
    void replaceActivePlatformKey(String purpose);

    void addPlatformKey(UUID id, String purpose, String keyRef, byte[] certificate, String subject, String ruc, Instant validFrom,
            Instant validUntil);

    /** Runs with or without a transaction. */
    Optional<PlatformKeyRow> getActivePlatformKey(String purpose);
}
