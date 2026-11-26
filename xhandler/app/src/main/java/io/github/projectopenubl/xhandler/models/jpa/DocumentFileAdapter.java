package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.models.DocumentFileModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentFileEntity;
import io.github.projectopenubl.xhandler.utils.Ids;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class DocumentFileAdapter implements DocumentFileModel, JpaModel<DocumentFileEntity> {

    private final DocumentFileEntity entity;

    public DocumentFileAdapter(DocumentFileEntity entity) {
        this.entity = entity;
    }

    public static DocumentFileEntity toEntity(DocumentFileModel model) {
        return ((DocumentFileAdapter) model).getEntity();
    }

    @Override
    public DocumentFileEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.DOCUMENT_FILE;
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
    public String getKind() {
        return entity.getKind();
    }

    @Override
    public void setKind(String kind) {
        entity.setKind(kind);
    }

    @Override
    public String getStorageKey() {
        return entity.getStorageKey();
    }

    @Override
    public void setStorageKey(String storageKey) {
        entity.setStorageKey(storageKey);
    }

    @Override
    public String getSha256() {
        return entity.getSha256();
    }

    @Override
    public void setSha256(String sha256) {
        entity.setSha256(sha256);
    }

    @Override
    public long getSize() {
        return entity.getSize();
    }

    @Override
    public void setSize(long size) {
        entity.setSize(size);
    }

    @Override
    public Instant getCreatedAt() {
        return entity.getCreatedAt();
    }

    @Override
    public void setCreatedAt(Instant createdAt) {
        entity.setCreatedAt(createdAt);
    }
}
