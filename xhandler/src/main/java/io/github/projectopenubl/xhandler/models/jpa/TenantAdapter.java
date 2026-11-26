package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.TenantEntity;
import java.time.Instant;
import java.util.UUID;

public class TenantAdapter implements TenantModel, JpaModel<TenantEntity> {

    private final TenantEntity entity;

    public TenantAdapter(TenantEntity entity) {
        this.entity = entity;
    }

    public static TenantEntity toEntity(TenantModel model) {
        return ((TenantAdapter) model).getEntity();
    }

    @Override
    public TenantEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.TENANT;
    }

    @Override
    public UUID getPartnerId() {
        return entity.getPartnerId();
    }

    @Override
    public void setPartnerId(UUID partnerId) {
        entity.setPartnerId(partnerId);
    }

    @Override
    public String getName() {
        return entity.getName();
    }

    @Override
    public void setName(String name) {
        entity.setName(name);
    }

    @Override
    public String getPlan() {
        return entity.getPlan();
    }

    @Override
    public void setPlan(String plan) {
        entity.setPlan(plan);
    }

    @Override
    public String getStatus() {
        return entity.getStatus();
    }

    @Override
    public void setStatus(String status) {
        entity.setStatus(status);
    }

    @Override
    public int getRetentionYears() {
        return entity.getRetentionYears();
    }

    @Override
    public void setRetentionYears(int retentionYears) {
        entity.setRetentionYears(retentionYears);
    }

    @Override
    public boolean isAuditHold() {
        return entity.isAuditHold();
    }

    @Override
    public void setAuditHold(boolean auditHold) {
        entity.setAuditHold(auditHold);
    }

    @Override
    public String getCreatedBy() {
        return entity.getCreatedBy();
    }

    @Override
    public void setCreatedBy(String createdBy) {
        entity.setCreatedBy(createdBy);
    }

    @Override
    public boolean isSendingPaused() {
        return entity.isSendingPaused();
    }

    @Override
    public void setSendingPaused(boolean sendingPaused) {
        entity.setSendingPaused(sendingPaused);
    }

    @Override
    public String getNotificationEmail() {
        return entity.getNotificationEmail();
    }

    @Override
    public void setNotificationEmail(String notificationEmail) {
        entity.setNotificationEmail(notificationEmail);
    }

    @Override
    public String getBillingRuc() {
        return entity.getBillingRuc();
    }

    @Override
    public void setBillingRuc(String billingRuc) {
        entity.setBillingRuc(billingRuc);
    }

    @Override
    public String getBillingName() {
        return entity.getBillingName();
    }

    @Override
    public void setBillingName(String billingName) {
        entity.setBillingName(billingName);
    }

    @Override
    public String getBillingEmail() {
        return entity.getBillingEmail();
    }

    @Override
    public void setBillingEmail(String billingEmail) {
        entity.setBillingEmail(billingEmail);
    }

    @Override
    public boolean isArchivePlan() {
        return entity.isArchivePlan();
    }

    @Override
    public void setArchivePlan(boolean archivePlan) {
        entity.setArchivePlan(archivePlan);
    }

    @Override
    public Instant getExitRequestedAt() {
        return entity.getExitRequestedAt();
    }

    @Override
    public void setExitRequestedAt(Instant exitRequestedAt) {
        entity.setExitRequestedAt(exitRequestedAt);
    }

    @Override
    public Instant getDeleteAfter() {
        return entity.getDeleteAfter();
    }

    @Override
    public void setDeleteAfter(Instant deleteAfter) {
        entity.setDeleteAfter(deleteAfter);
    }

    @Override
    public Instant getCreatedAt() {
        return entity.getCreatedAt();
    }

    @Override
    public void setCreatedAt(Instant createdAt) {
        entity.setCreatedAt(createdAt);
    }

    @Override
    public Instant getUpdatedAt() {
        return entity.getUpdatedAt();
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        entity.setUpdatedAt(updatedAt);
    }

    @Override
    public Instant getDeletedAt() {
        return entity.getDeletedAt();
    }

    @Override
    public void setDeletedAt(Instant deletedAt) {
        entity.setDeletedAt(deletedAt);
    }
}
