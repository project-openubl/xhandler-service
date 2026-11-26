package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.SeriesModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.SeriesEntity;
import java.time.Instant;
import java.util.UUID;

public class SeriesAdapter implements SeriesModel, JpaModel<SeriesEntity> {

    private final SeriesEntity entity;

    public SeriesAdapter(SeriesEntity entity) {
        this.entity = entity;
    }

    public static SeriesEntity toEntity(SeriesModel model) {
        return ((SeriesAdapter) model).getEntity();
    }

    @Override
    public SeriesEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.SERIES;
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
    public String getSunatEnv() {
        return entity.getSunatEnv();
    }

    @Override
    public void setSunatEnv(String sunatEnv) {
        entity.setSunatEnv(sunatEnv);
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
    public long getNextNumber() {
        return entity.getNextNumber();
    }

    @Override
    public void setNextNumber(long nextNumber) {
        entity.setNextNumber(nextNumber);
    }

    @Override
    public boolean isDefault() {
        return entity.isDefault();
    }

    @Override
    public void setDefault(boolean isDefault) {
        entity.setDefault(isDefault);
    }

    @Override
    public boolean isActive() {
        return entity.isActive();
    }

    @Override
    public void setActive(boolean active) {
        entity.setActive(active);
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
