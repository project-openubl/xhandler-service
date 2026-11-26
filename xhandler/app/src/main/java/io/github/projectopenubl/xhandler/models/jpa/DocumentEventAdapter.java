package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.DocumentEventModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEventEntity;
import io.github.projectopenubl.xhandler.utils.Ids;
import java.time.Instant;
import java.util.UUID;

public class DocumentEventAdapter implements DocumentEventModel, JpaModel<DocumentEventEntity> {

    private final DocumentEventEntity entity;

    public DocumentEventAdapter(DocumentEventEntity entity) {
        this.entity = entity;
    }

    public static DocumentEventEntity toEntity(DocumentEventModel model) {
        return ((DocumentEventAdapter) model).getEntity();
    }

    @Override
    public DocumentEventEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.DOCUMENT_EVENT;
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
    public UUID getDocumentId() {
        return entity.getDocumentId();
    }

    @Override
    public void setDocumentId(UUID documentId) {
        entity.setDocumentId(documentId);
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
    public Integer getCode() {
        return entity.getCode();
    }

    @Override
    public void setCode(Integer code) {
        entity.setCode(code);
    }

    @Override
    public String getMessage() {
        return entity.getMessage();
    }

    @Override
    public void setMessage(String message) {
        entity.setMessage(message);
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
    public String getCorrelationId() {
        return entity.getCorrelationId();
    }

    @Override
    public void setCorrelationId(String correlationId) {
        entity.setCorrelationId(correlationId);
    }
}
