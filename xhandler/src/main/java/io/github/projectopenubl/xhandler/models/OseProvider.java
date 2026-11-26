package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage of the OSE role (DESIGN.md §11.4): its users, SUNAT's association and blocked lists, the
 * submissions it checked, their tickets, usage and forwarding queue. Platform-level tables, outside
 * any tenant; every method joins the caller's transaction.
 */
public interface OseProvider {

    /** An OSE user as the login needs it, whatever its status. */
    record ClientLogin(UUID id, String issuerRuc, String username, UUID tenantId, String passwordHash, String status) {}

    record Client(UUID id, String issuerRuc, String username, UUID tenantId) {}

    record ClientView(UUID id, String issuerRuc, String username, UUID tenantId, String status, Instant lockedUntil, Instant lastLoginAt) {}

    record Usage(UUID clientId, String issuerRuc, String username, UUID tenantId, long validations) {}

    /** The forwarding queue against the 1-hour limit; {@code oldestPendingSeconds} is 0 with nothing pending. */
    record ForwardingStats(long pending, double oldestPendingSeconds, long rejected, long late, long forwarded) {}

    record RejectedForward(UUID id, String name, String lastError, Instant cdrIssuedAt) {}

    /** The accepted submission of a document number. */
    record AcceptedSubmission(String sha256, String cdrRef, UUID id) {}

    record NewSubmission(UUID id, UUID clientId, String issuerRuc, String tipo, String serie, long numero, String name, String channel,
            String ticket, Instant receivedAt, String result, String responseCode, String description, String sha256, String xmlRef,
            String cdrRef, Instant cdrIssuedAt, String forwardStatus, Instant nextAttemptAt, Instant purgeAfter) {}

    record TicketItem(String name, String result, String responseCode, String description, String cdrRef) {}

    record SubmissionCdr(String name, String cdrRef) {}

    /** A submission leased for forwarding. */
    record DueForward(UUID id, String name, String tipo, String xmlRef, String cdrRef, Instant cdrIssuedAt, int attempts) {}

    /** The files of a purged submission (either may be null). */
    record PurgedFiles(String xmlRef, String cdrRef) {}

    // Users

    void addClient(UUID id, String issuerRuc, String username, String passwordHash, UUID tenantId, String createdBy);

    List<ClientView> getClients();

    Optional<ClientLogin> getClientLogin(String username);

    Optional<Client> getActiveClient(String username);

    /** Counts one more failed login; returns the consecutive failures. */
    int countFailedLogin(UUID id);

    /** Clears the failures and stamps the login (at most once a minute while nothing failed). */
    void markLogin(UUID id);

    int suspendClient(UUID id);

    int activateClient(UUID id);

    int resetClientPassword(UUID id, String passwordHash);

    // SUNAT's lists

    void deleteAssociations();

    void addAssociation(String ruc, LocalDate startsOn, LocalDate endsOn);

    /** Associated on {@code today}: the relation counts until the 7th day of the month after it ends. */
    boolean isAssociated(String ruc, LocalDate today);

    void deleteBlockedList(String list);

    void addBlockedIssuer(String ruc, String list, LocalDate listedOn);

    boolean isBlocked(String ruc);

    // Submissions, tickets and usage

    Optional<AcceptedSubmission> getAcceptedSubmission(String issuerRuc, String tipo, String serie, long numero);

    void addSubmission(NewSubmission submission);

    void addTicketItem(String ticket, UUID clientId, UUID submissionId);

    /** Same as {@link #addTicketItem}, ignoring an existing one. */
    void linkTicketItem(String ticket, UUID clientId, UUID submissionId);

    void countValidation(UUID clientId, LocalDate day);

    List<TicketItem> getTicketItems(String ticket, UUID clientId);

    Optional<SubmissionCdr> getSubmissionCdr(String issuerRuc, String tipo, String serie, long numero);

    List<Usage> getUsage(LocalDate from, LocalDate to);

    // Forwarding

    ForwardingStats getForwardingStats();

    List<RejectedForward> getRejectedForwards();

    long countBlockedForwards();

    /** Age in seconds of the oldest CDR-OSE not yet forwarded; 0 with none. */
    double getOldestForwardSeconds();

    /** Puts a rejected forward back in the queue; returns how many rows changed. */
    int markReforward(UUID id);

    /** Blocked rows whose wait is over go back to pending. */
    int unblockDueForwards();

    /** Leases up to 50 due rows for 2 minutes ({@code FOR UPDATE SKIP LOCKED}). */
    List<DueForward> leaseForwards(UUID lease);

    /** Gives a leased row back at once. */
    int releaseForward(UUID id, UUID lease);

    /** A failed send on the lease: retried after {@code wait} (a PostgreSQL interval). */
    int retryForward(UUID id, UUID lease, String error, String wait);

    int markForwarded(UUID id, UUID lease, String ack);

    int markForwardRejected(UUID id, UUID lease, String error);

    int markForwardBlocked(UUID id, UUID lease, String error);

    /** Deletes the submissions past their retention (inconsistencies and forwarded ones). */
    List<PurgedFiles> deletePurgeableSubmissions();

    /** Those of {@code refs} still referenced by a submission. */
    List<String> getReferencedFiles(List<String> refs);
}
