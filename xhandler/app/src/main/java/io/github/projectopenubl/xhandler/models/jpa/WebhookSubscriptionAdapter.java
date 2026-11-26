package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.WebhookSubscriptionModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.WebhookSubscriptionEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class WebhookSubscriptionAdapter implements WebhookSubscriptionModel, JpaModel<WebhookSubscriptionEntity> {

    private final WebhookSubscriptionEntity entity;

    public WebhookSubscriptionAdapter(WebhookSubscriptionEntity entity) {
        this.entity = entity;
    }

    public static WebhookSubscriptionEntity toEntity(WebhookSubscriptionModel model) {
        return ((WebhookSubscriptionAdapter) model).getEntity();
    }

    @Override
    public WebhookSubscriptionEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.WEBHOOK_SUBSCRIPTION;
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
    public String getUrl() {
        return entity.getUrl();
    }

    @Override
    public void setUrl(String url) {
        entity.setUrl(url);
    }

    @Override
    public String[] getEvents() {
        return entity.getEvents();
    }

    @Override
    public void setEvents(String[] events) {
        entity.setEvents(events);
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

    @Override
    public Instant getDeletedAt() {
        return entity.getDeletedAt();
    }

    @Override
    public void setDeletedAt(Instant deletedAt) {
        entity.setDeletedAt(deletedAt);
    }
}
