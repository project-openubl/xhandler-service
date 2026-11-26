package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.github.projectopenubl.xhandler.models.CredentialModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.CredentialEntity;
import java.time.Instant;
import java.util.UUID;

public class CredentialAdapter implements CredentialModel, JpaModel<CredentialEntity> {

    private final CredentialEntity entity;

    public CredentialAdapter(CredentialEntity entity) {
        this.entity = entity;
    }

    public static CredentialEntity toEntity(CredentialModel model) {
        return ((CredentialAdapter) model).getEntity();
    }

    @Override
    public CredentialEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.CREDENTIAL;
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
    public String getLabel() {
        return entity.getLabel();
    }

    @Override
    public void setLabel(String label) {
        entity.setLabel(label);
    }

    @Override
    public byte[] getCiphertext() {
        return entity.getCiphertext();
    }

    @Override
    public void setCiphertext(byte[] ciphertext) {
        entity.setCiphertext(ciphertext);
    }

    @Override
    public byte[] getWrappedDataKey() {
        return entity.getWrappedDataKey();
    }

    @Override
    public void setWrappedDataKey(byte[] wrappedDataKey) {
        entity.setWrappedDataKey(wrappedDataKey);
    }

    @Override
    public String getMasterKeyId() {
        return entity.getMasterKeyId();
    }

    @Override
    public void setMasterKeyId(String masterKeyId) {
        entity.setMasterKeyId(masterKeyId);
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
