package io.github.projectopenubl.xhandler.models.jpa.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "tenant")
public class TenantEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    private UUID partnerId;
    private String name;
    private String plan = "free";
    private String status = "active";
    private int retentionYears = 10;
    private boolean auditHold;
    private String createdBy;
    /** Holds SUNAT attempts for all its documents (DESIGN.md §24.1) */
    private boolean sendingPaused;
    /** Where tenant notifications are emailed (DESIGN.md §26.6) */
    private String notificationEmail;
    private String billingRuc;
    private String billingName;
    private String billingEmail;
    /** Exit (DESIGN.md §13): kept past the exit period instead of deleted */
    private boolean archivePlan;
    private Instant exitRequestedAt;
    private Instant deleteAfter;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant deletedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(UUID partnerId) {
        this.partnerId = partnerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRetentionYears() {
        return retentionYears;
    }

    public void setRetentionYears(int retentionYears) {
        this.retentionYears = retentionYears;
    }

    public boolean isAuditHold() {
        return auditHold;
    }

    public void setAuditHold(boolean auditHold) {
        this.auditHold = auditHold;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public boolean isSendingPaused() {
        return sendingPaused;
    }

    public void setSendingPaused(boolean sendingPaused) {
        this.sendingPaused = sendingPaused;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }

    public String getBillingRuc() {
        return billingRuc;
    }

    public void setBillingRuc(String billingRuc) {
        this.billingRuc = billingRuc;
    }

    public String getBillingName() {
        return billingName;
    }

    public void setBillingName(String billingName) {
        this.billingName = billingName;
    }

    public String getBillingEmail() {
        return billingEmail;
    }

    public void setBillingEmail(String billingEmail) {
        this.billingEmail = billingEmail;
    }

    public boolean isArchivePlan() {
        return archivePlan;
    }

    public void setArchivePlan(boolean archivePlan) {
        this.archivePlan = archivePlan;
    }

    public Instant getExitRequestedAt() {
        return exitRequestedAt;
    }

    public void setExitRequestedAt(Instant exitRequestedAt) {
        this.exitRequestedAt = exitRequestedAt;
    }

    public Instant getDeleteAfter() {
        return deleteAfter;
    }

    public void setDeleteAfter(Instant deleteAfter) {
        this.deleteAfter = deleteAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
