package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data protection (DESIGN.md §13, §16): data-subject requests, the personal data held about a
 * person, and the tenant lifecycle flags platform admins set. Every method joins the caller's
 * transaction, which entered the tenant (except {@link #getRequestsDue}, a SECURITY DEFINER function).
 */
public interface PrivacyProvider {

    /** A row of {@code data_subject_request}. */
    record DataSubjectRequestRow(UUID id, String subjectDocType, String subjectDocNumber, String subjectName, String kind, String channel,
            String details, Instant receivedAt, Instant forwardedAt, Instant responseDueAt, String status, String resolution, Instant closedAt) {}

    /** A document issued to the person; the values other than the id, RUC, type and status are as read from the database. */
    record PersonalDocumentRow(UUID id, String issuerRuc, String tipoComprobante, Object serie, Object numero, Object fechaEmision, Object nombre,
            Object moneda, Object total, String status) {}

    /** A draft naming the person as buyer, deleted ones too. */
    record PersonalDraftRow(UUID id, String issuerRuc, String kind, String tipoComprobante, String serie, String nombre, String status,
            Instant createdAt, Instant deletedAt) {}

    /** An open request close to (or past) its response term. */
    record DueRequest(UUID tenantId, UUID requestId, Instant responseDueAt) {}

    void addRequest(UUID id, UUID tenantId, String subjectDocType, String subjectDocNumber, String subjectName, String kind, String channel,
            String details, Instant receivedAt, Instant forwardDueAt, Instant forwardedAt, Instant responseDueAt, String createdBy);

    /** Ids of a page of requests, the most recent first; {@code status} null for every status. */
    List<UUID> getRequestIds(String status, int limit, int offset);

    Optional<DataSubjectRequestRow> getRequest(UUID id);

    /** Closes an open request; returns the rows changed (0: unknown or already closed). */
    int closeRequest(UUID id, String resolution, String closedBy);

    /** At most 5000 documents issued to the person, the most recent first. */
    List<PersonalDocumentRow> getPersonalDocuments(String docType, String docNumber);

    /** At most 5000 drafts naming the person as buyer, the most recent first. */
    List<PersonalDraftRow> getPersonalDrafts(String docType, String docNumber);

    /** Open requests of every tenant due within 2 days ({@code xhandler.privacy_requests_due(2)}). */
    List<DueRequest> getRequestsDue();

    /** Marks an open request as reminded; returns 1 the first time, 0 after. */
    int markReminded(UUID id);

    /** Sets the tenant's tax-audit hold; returns the rows changed (0: unknown or deleted tenant). */
    int setAuditHold(UUID tenantId, boolean value);

    /** Sets the tenant's archive plan; returns the rows changed (0: unknown or deleted tenant). */
    int setArchivePlan(UUID tenantId, boolean value);

    /** An exited tenant without deletion date (it had the archive plan) gets one: the 2-year limit, at least 30 days away. */
    void scheduleExitedTenantDeletion(UUID tenantId);

    /** Brings an exited, not yet deleted tenant back to active; returns the rows changed. */
    int cancelExit(UUID tenantId);

    /** Whether the tenant exists and is not deleted. */
    boolean existsTenant(UUID tenantId);
}
