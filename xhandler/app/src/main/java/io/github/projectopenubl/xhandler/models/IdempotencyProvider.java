package io.github.projectopenubl.xhandler.models;

import java.util.Optional;
import java.util.UUID;

/** {@code Idempotency-Key} rows (DESIGN.md §11.1); every method joins the caller's transaction, which entered the tenant. */
public interface IdempotencyProvider {

    /**
     * A stored key: {@code statusCode} and {@code response} (JSON text) are null while the first
     * request runs; {@code stale} if it was claimed more than two minutes ago.
     */
    record IdempotencyKey(String requestHash, Integer statusCode, String response, boolean stale) {}

    /** Inserts the key with {@code claim}; false if it already exists. */
    boolean insertKey(UUID tenantId, String key, String requestHash, UUID claim);

    /** The key, locking the row until the transaction ends. */
    Optional<IdempotencyKey> getKeyForUpdate(UUID tenantId, String key);

    /** Takes over an abandoned key with a new claim. */
    void takeOverKey(UUID tenantId, String key, UUID claim);

    /** Stores the response if {@code claim} still holds the key and it isn't completed; returns how many rows changed. */
    int completeKey(UUID tenantId, String key, UUID claim, int status, String body);

    /** Deletes the key if {@code claim} still holds it and it isn't completed. */
    void deleteKey(UUID tenantId, String key, UUID claim);
}
