package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.UUID;

public interface TenantModel extends Model {

    UUID getPartnerId();

    void setPartnerId(UUID partnerId);

    String getName();

    void setName(String name);

    String getStatus();

    void setStatus(String status);

    int getRetentionYears();

    void setRetentionYears(int retentionYears);

    boolean isAuditHold();

    void setAuditHold(boolean auditHold);

    String getCreatedBy();

    void setCreatedBy(String createdBy);

    /** Holds SUNAT attempts for all its documents (DESIGN.md §24.1) */
    boolean isSendingPaused();

    void setSendingPaused(boolean sendingPaused);

    /** Where tenant notifications are emailed (DESIGN.md §26.6) */
    String getNotificationEmail();

    void setNotificationEmail(String notificationEmail);

    /** Exit (DESIGN.md §13): kept past the exit period instead of deleted */
    boolean isArchivePlan();

    void setArchivePlan(boolean archivePlan);

    Instant getExitRequestedAt();

    void setExitRequestedAt(Instant exitRequestedAt);

    Instant getDeleteAfter();

    void setDeleteAfter(Instant deleteAfter);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    Instant getDeletedAt();

    void setDeletedAt(Instant deletedAt);
}
