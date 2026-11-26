package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.AuditLogModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.AuditLogEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AuditLogAdapter implements AuditLogModel, JpaModel<AuditLogEntity> {

    private final AuditLogEntity entity;

    public AuditLogAdapter(AuditLogEntity entity) {
        this.entity = entity;
    }

    public static AuditLogEntity toEntity(AuditLogModel model) {
        return ((AuditLogAdapter) model).getEntity();
    }

    @Override
    public AuditLogEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.AUDIT_LOG;
    }

    @Override
    public Instant getAt() {
        return entity.getAt();
    }

    @Override
    public void setAt(Instant at) {
        entity.setAt(at);
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
    public String getActor() {
        return entity.getActor();
    }

    @Override
    public void setActor(String actor) {
        entity.setActor(actor);
    }

    @Override
    public String getAction() {
        return entity.getAction();
    }

    @Override
    public void setAction(String action) {
        entity.setAction(action);
    }

    @Override
    public String getObjectType() {
        return entity.getObjectType();
    }

    @Override
    public void setObjectType(String objectType) {
        entity.setObjectType(objectType);
    }

    @Override
    public String getObjectId() {
        return entity.getObjectId();
    }

    @Override
    public void setObjectId(String objectId) {
        entity.setObjectId(objectId);
    }

    @Override
    public Map<String, Object> getDetails() {
        return entity.getDetails();
    }

    @Override
    public void setDetails(Map<String, Object> details) {
        entity.setDetails(details);
    }
}
