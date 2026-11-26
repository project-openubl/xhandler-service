package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.OseEndpointModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.OseEndpointEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class OseEndpointAdapter implements OseEndpointModel, JpaModel<OseEndpointEntity> {

    private final OseEndpointEntity entity;

    public OseEndpointAdapter(OseEndpointEntity entity) {
        this.entity = entity;
    }

    public static OseEndpointEntity toEntity(OseEndpointModel model) {
        return ((OseEndpointAdapter) model).getEntity();
    }

    @Override
    public OseEndpointEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.OSE_ENDPOINT;
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
    public String getName() {
        return entity.getName();
    }

    @Override
    public void setName(String name) {
        entity.setName(name);
    }

    @Override
    public String getInvoiceUrl() {
        return entity.getInvoiceUrl();
    }

    @Override
    public void setInvoiceUrl(String invoiceUrl) {
        entity.setInvoiceUrl(invoiceUrl);
    }

    @Override
    public String getPerceptionUrl() {
        return entity.getPerceptionUrl();
    }

    @Override
    public void setPerceptionUrl(String perceptionUrl) {
        entity.setPerceptionUrl(perceptionUrl);
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
