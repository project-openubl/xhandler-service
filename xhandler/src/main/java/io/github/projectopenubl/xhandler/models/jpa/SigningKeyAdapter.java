package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.SigningKeyModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.SigningKeyEntity;
import java.time.Instant;
import java.util.UUID;

public class SigningKeyAdapter implements SigningKeyModel, JpaModel<SigningKeyEntity> {

    private final SigningKeyEntity entity;

    public SigningKeyAdapter(SigningKeyEntity entity) {
        this.entity = entity;
    }

    public static SigningKeyEntity toEntity(SigningKeyModel model) {
        return ((SigningKeyAdapter) model).getEntity();
    }

    @Override
    public SigningKeyEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.SIGNING_KEY;
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
    public String getKeyRef() {
        return entity.getKeyRef();
    }

    @Override
    public void setKeyRef(String keyRef) {
        entity.setKeyRef(keyRef);
    }

    @Override
    public byte[] getCertificate() {
        return entity.getCertificate();
    }

    @Override
    public void setCertificate(byte[] certificate) {
        entity.setCertificate(certificate);
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
    public String getIssuer() {
        return entity.getIssuer();
    }

    @Override
    public void setIssuer(String issuer) {
        entity.setIssuer(issuer);
    }

    @Override
    public String getSerial() {
        return entity.getSerial();
    }

    @Override
    public void setSerial(String serial) {
        entity.setSerial(serial);
    }

    @Override
    public String getRuc() {
        return entity.getRuc();
    }

    @Override
    public void setRuc(String ruc) {
        entity.setRuc(ruc);
    }

    @Override
    public Instant getValidFrom() {
        return entity.getValidFrom();
    }

    @Override
    public void setValidFrom(Instant validFrom) {
        entity.setValidFrom(validFrom);
    }

    @Override
    public Instant getValidUntil() {
        return entity.getValidUntil();
    }

    @Override
    public void setValidUntil(Instant validUntil) {
        entity.setValidUntil(validUntil);
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
