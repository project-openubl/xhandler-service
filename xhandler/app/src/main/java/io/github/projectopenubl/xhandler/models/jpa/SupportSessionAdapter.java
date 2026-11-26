package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.SupportSessionModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.SupportSessionEntity;
import java.time.Instant;
import java.util.UUID;

public class SupportSessionAdapter implements SupportSessionModel, JpaModel<SupportSessionEntity> {

    private final SupportSessionEntity entity;

    public SupportSessionAdapter(SupportSessionEntity entity) {
        this.entity = entity;
    }

    public static SupportSessionEntity toEntity(SupportSessionModel model) {
        return ((SupportSessionAdapter) model).getEntity();
    }

    @Override
    public SupportSessionEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.SUPPORT_SESSION;
    }

    @Override
    public UUID getTenantId() {
        return entity.getTenantId();
    }

    @Override
    public void setTenantId(UUID tenantId) {
        entity.setTenantId(tenantId);
    }

    @Override
    public String getOperator() {
        return entity.getOperator();
    }

    @Override
    public void setOperator(String operator) {
        entity.setOperator(operator);
    }

    @Override
    public String getReason() {
        return entity.getReason();
    }

    @Override
    public void setReason(String reason) {
        entity.setReason(reason);
    }

    @Override
    public Instant getRequestedAt() {
        return entity.getRequestedAt();
    }

    @Override
    public void setRequestedAt(Instant requestedAt) {
        entity.setRequestedAt(requestedAt);
    }

    @Override
    public String getApprovedBy() {
        return entity.getApprovedBy();
    }

    @Override
    public void setApprovedBy(String approvedBy) {
        entity.setApprovedBy(approvedBy);
    }

    @Override
    public Instant getApprovedAt() {
        return entity.getApprovedAt();
    }

    @Override
    public void setApprovedAt(Instant approvedAt) {
        entity.setApprovedAt(approvedAt);
    }

    @Override
    public Instant getExpiresAt() {
        return entity.getExpiresAt();
    }

    @Override
    public void setExpiresAt(Instant expiresAt) {
        entity.setExpiresAt(expiresAt);
    }

    @Override
    public Instant getRevokedAt() {
        return entity.getRevokedAt();
    }

    @Override
    public void setRevokedAt(Instant revokedAt) {
        entity.setRevokedAt(revokedAt);
    }
}
