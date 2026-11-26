package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage of SIRE (DESIGN.md §11.7): the periods of each company register, SUNAT's calendar and the
 * deadline notices. Every method joins the caller's transaction, which entered the tenant (the
 * calendar and the cross-tenant SECURITY DEFINER functions excepted).
 */
public interface SireProvider {

    record PeriodState(UUID id, String status) {}

    /** A period's status and its reconciliation (JSON text, null before the proposal). */
    record ReplaceState(String status, String reconciliation) {}

    record Period(UUID id, UUID companyId, String sunatEnv, String book, String period, String status, String proposalTicket,
            String replacementTicket, int attempts) {}

    /** A period as the API shows it; {@code reconciliation} is JSON text. */
    record PeriodView(UUID id, String book, String period, String sunatEnv, String status, String detail, String reconciliation,
            boolean proposalFile, boolean replacementFile, Instant createdAt, Instant updatedAt) {}

    record PeriodSummary(UUID id, String book, String period, String sunatEnv, String status, String detail, boolean proposalFile,
            boolean replacementFile, Instant createdAt, Instant updatedAt) {}

    record PeriodToPoll(UUID tenantId, UUID periodId) {}

    record DeadlineDue(UUID tenantId, UUID companyId, String ruc, String period, LocalDate dueDate) {}

    /** {@code dueDate} as text ({@code yyyy-MM-dd}). */
    record CalendarEntry(String period, int rucLastDigit, String dueDate) {}

    // Periods

    /** Creates the period in {@code requested} unless it exists; returns how many rows were inserted. */
    int addPeriod(UUID id, UUID tenantId, UUID companyId, String sunatEnv, String book, String period, String requestedBy);

    /** The period, locked until the transaction ends; it must exist. */
    PeriodState getPeriodStateForUpdate(UUID companyId, String sunatEnv, String book, String period);

    /** Back to {@code requested}, dropping the proposal, its reconciliation and the polling state. */
    int restartPeriod(UUID id, String requestedBy);

    Optional<UUID> getPeriodId(UUID companyId, String sunatEnv, String book, String period);

    /** Status and reconciliation, locked until the transaction ends; the period must exist. */
    ReplaceState getReplaceStateForUpdate(UUID id);

    /** Moves the period to {@code submitting}. */
    int markSubmitting(UUID id);

    PeriodView getPeriodView(UUID id);

    List<PeriodSummary> getPeriods(UUID companyId, String sunatEnv, String book, int limit, int offset);

    /** The file key of SUNAT's proposal, if stored. */
    Optional<String> getProposalRef(UUID id);

    /** The file key of the submitted replacement, if stored. */
    Optional<String> getReplacementRef(UUID id);

    Period getPeriod(UUID id);

    /**
     * Compare-and-set: moves the period from {@code from} (and, when {@code ticketColumn} is given, the
     * ticket it acted on) to {@code to}, setting {@code columns}; returns the rows changed.
     */
    int transitionPeriod(UUID id, String from, String ticketColumn, String ticket, String to, Map<String, Object> columns);

    /** Periods of the company waiting for credentials resume; returns how many. */
    int resumeAfterCredentials(UUID companyId);

    /** Claims the proposal request of a {@code requested} period (or a claim older than 10 minutes). */
    int claimProposal(UUID id, String claim);

    /** Periods with a ticket to follow, every tenant (SECURITY DEFINER). */
    List<PeriodToPoll> getPeriodsToPoll();

    // Deadlines and calendar

    /** Sales registers due within 3 days that SUNAT hasn't accepted, every tenant (SECURITY DEFINER). */
    List<DeadlineDue> getDeadlinesDue();

    /** Records the deadline notice of a company and period; returns 0 if it was sent already. */
    int addDeadlineNotice(UUID tenantId, UUID companyId, String period);

    void putCalendarEntry(String period, int rucLastDigit, LocalDate dueDate);

    /** The calendar, one period or all of them when {@code period} is null. */
    List<CalendarEntry> getCalendar(String period);
}
