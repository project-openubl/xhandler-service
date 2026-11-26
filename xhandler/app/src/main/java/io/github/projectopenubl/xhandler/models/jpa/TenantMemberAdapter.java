package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.TenantMemberModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.TenantMemberEntity;
import java.time.Instant;
import java.util.UUID;

public class TenantMemberAdapter implements TenantMemberModel, JpaModel<TenantMemberEntity> {

    private final TenantMemberEntity entity;

    public TenantMemberAdapter(TenantMemberEntity entity) {
        this.entity = entity;
    }

    public static TenantMemberEntity toEntity(TenantMemberModel model) {
        return ((TenantMemberAdapter) model).getEntity();
    }

    @Override
    public TenantMemberEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.TENANT_MEMBER;
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
    public String getKind() {
        return entity.getKind();
    }

    @Override
    public void setKind(String kind) {
        entity.setKind(kind);
    }

    @Override
    public String getSubject() {
        return entity.getSubject();
    }

    @Override
    public void setSubject(String subject) {
        entity.setSubject(subject);
    }

    @Override
    public String getRole() {
        return entity.getRole();
    }

    @Override
    public void setRole(String role) {
        entity.setRole(role);
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
}
