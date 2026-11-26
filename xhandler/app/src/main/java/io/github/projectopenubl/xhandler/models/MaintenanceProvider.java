package io.github.projectopenubl.xhandler.models;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Platform maintenance (partitions, purges, retention) through SECURITY DEFINER functions; every
 * method joins the caller's transaction.
 */
public interface MaintenanceProvider {

    /** A document past its tenant's retention period. */
    record ExpiredDocument(UUID tenantId, UUID documentId) {}

    /** Creates the partitions of document and document_event that {@code day} needs. */
    void ensurePartitions(LocalDate day);

    /** Deletes idempotency keys older than 24 hours; returns how many. */
    int purgeIdempotencyKeys();

    /** Deletes public lookup rate-limit buckets older than a day; returns how many. */
    int deleteOldRateBuckets();

    /** At most 500 documents whose retention period is over. */
    List<ExpiredDocument> getExpiredDocuments();

    /** Deletes the tenant's documents with their events and links; returns the storage keys of their files. */
    List<String> purgeDocuments(UUID tenantId, List<UUID> documentIds);

    /** Deletes at most {@code limit} drafts soft-deleted more than {@code days} days ago; returns how many. */
    int purgeDeletedDrafts(int days, int limit);
}
