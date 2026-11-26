package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform administration queries (DESIGN.md §15, §24.1): cross-tenant lookups and platform-wide
 * tables. Every method joins the caller's transaction.
 */
public interface PlatformProvider {

    record EndpointRow(String id, String breakerState, Instant breakerUntil, int concurrencyCap, boolean paused) {}

    record MigrationRow(String version, String description, boolean success, LocalDateTime installedOn) {}

    record JobSummary(String name, String state, String worker, String error) {}

    /** The tenant of a document, whichever tenant it is (SECURITY DEFINER). */
    Optional<UUID> getDocumentTenant(UUID documentId);


    void createPartner(UUID id, String name);

    /** False when the partner does not exist. */
    boolean setPartnerMember(UUID partnerId, String kind, String subject, String role);

    /** False when the partner or the member does not exist. */
    boolean removePartnerMember(UUID partnerId, String kind, String subject);

    /** Inserts or overwrites a {@code platform_setting}. */
    void setPlatformSetting(String key, String value);

    List<EndpointRow> getEndpoints();

    EndpointRow getEndpoint(String id);

    /** Returns the number of updated rows (0: unknown endpoint). */
    int setEndpointPaused(String id, boolean paused);

    /** Returns the number of updated rows (0: unknown endpoint). */
    int setEndpointConcurrencyCap(String id, int concurrencyCap);

    /** Cancels any job; the new state, or empty when it does not exist or already finished. */
    Optional<String> cancelJob(UUID id);

    List<MigrationRow> getMigrations();


    /** Makes due again the stuck non-definitive documents; returns how many. */
    int requeueStuckDocuments();

    Optional<JobSummary> getJobSummary(UUID id);

}
