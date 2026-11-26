package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.DocumentDraftModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentDraftEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DocumentDraftAdapter implements DocumentDraftModel, JpaModel<DocumentDraftEntity> {

    private final DocumentDraftEntity entity;

    public DocumentDraftAdapter(DocumentDraftEntity entity) {
        this.entity = entity;
    }

    public static DocumentDraftEntity toEntity(DocumentDraftModel model) {
        return ((DocumentDraftAdapter) model).getEntity();
    }

    @Override
    public DocumentDraftEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.DOCUMENT_DRAFT;
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
    public UUID getCompanyId() {
        return entity.getCompanyId();
    }

    @Override
    public void setCompanyId(UUID companyId) {
        entity.setCompanyId(companyId);
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
    public String getTipoComprobante() {
        return entity.getTipoComprobante();
    }

    @Override
    public void setTipoComprobante(String tipoComprobante) {
        entity.setTipoComprobante(tipoComprobante);
    }

    @Override
    public String getSerie() {
        return entity.getSerie();
    }

    @Override
    public void setSerie(String serie) {
        entity.setSerie(serie);
    }

    @Override
    public Map<String, Object> getSpec() {
        return entity.getSpec();
    }

    @Override
    public void setSpec(Map<String, Object> spec) {
        entity.setSpec(spec);
    }

    @Override
    public Map<String, String> getLabels() {
        return entity.getLabels();
    }

    @Override
    public void setLabels(Map<String, String> labels) {
        entity.setLabels(labels);
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
    public int getVersion() {
        return entity.getVersion();
    }

    @Override
    public void setVersion(int version) {
        entity.setVersion(version);
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
    public String getCreatedBy() {
        return entity.getCreatedBy();
    }

    @Override
    public void setCreatedBy(String createdBy) {
        entity.setCreatedBy(createdBy);
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
