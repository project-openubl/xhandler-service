package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company catalogs (DESIGN.md §17) and the RUC/DNI lookup counter; every method joins the caller's
 * transaction, which entered the tenant.
 */
public interface CatalogProvider {

    /** A catalog entry row; {@code data} is the JSON text. */
    record CatalogRow(UUID id, String kind, String key, String data, Instant createdAt, Instant updatedAt) {}

    /**
     * Live entries of a kind ordered by key; {@code q} (optional) searches key, name, description,
     * document, plate and address.
     */
    List<CatalogRow> searchEntries(UUID companyId, String kind, String q, int limit, int offset);

    Optional<CatalogRow> getEntry(UUID companyId, String kind, UUID id);

    Optional<CatalogRow> getEntryByKey(UUID companyId, String kind, String key);

    void addEntry(UUID id, UUID tenantId, UUID companyId, String kind, String key, String data, String deviceId);

    void updateEntry(UUID id, String key, String data);

    /** Soft-deletes the entry. */
    void deleteEntry(UUID id);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    /** Counts one hit for {@code key} in the current hour's bucket; returns the bucket's hits. */
    int countLookupHit(String key);
}
