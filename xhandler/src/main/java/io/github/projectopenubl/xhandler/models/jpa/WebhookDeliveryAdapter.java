package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.WebhookDeliveryModel;
import io.github.projectopenubl.xhandler.models.jpa.entities.WebhookDeliveryEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class WebhookDeliveryAdapter implements WebhookDeliveryModel, JpaModel<WebhookDeliveryEntity> {

    private final WebhookDeliveryEntity entity;

    public WebhookDeliveryAdapter(WebhookDeliveryEntity entity) {
        this.entity = entity;
    }

    public static WebhookDeliveryEntity toEntity(WebhookDeliveryModel model) {
        return ((WebhookDeliveryAdapter) model).getEntity();
    }

    @Override
    public WebhookDeliveryEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.WEBHOOK_DELIVERY;
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
    public UUID getSubscriptionId() {
        return entity.getSubscriptionId();
    }

    @Override
    public void setSubscriptionId(UUID subscriptionId) {
        entity.setSubscriptionId(subscriptionId);
    }

    @Override
    public UUID getEventId() {
        return entity.getEventId();
    }

    @Override
    public void setEventId(UUID eventId) {
        entity.setEventId(eventId);
    }

    @Override
    public String getEventType() {
        return entity.getEventType();
    }

    @Override
    public void setEventType(String eventType) {
        entity.setEventType(eventType);
    }

    @Override
    public Map<String, Object> getPayload() {
        return entity.getPayload();
    }

    @Override
    public void setPayload(Map<String, Object> payload) {
        entity.setPayload(payload);
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
    public int getAttempts() {
        return entity.getAttempts();
    }

    @Override
    public void setAttempts(int attempts) {
        entity.setAttempts(attempts);
    }

    @Override
    public Instant getNextAttemptAt() {
        return entity.getNextAttemptAt();
    }

    @Override
    public void setNextAttemptAt(Instant nextAttemptAt) {
        entity.setNextAttemptAt(nextAttemptAt);
    }

    @Override
    public Integer getLastStatusCode() {
        return entity.getLastStatusCode();
    }

    @Override
    public void setLastStatusCode(Integer lastStatusCode) {
        entity.setLastStatusCode(lastStatusCode);
    }

    @Override
    public String getLastError() {
        return entity.getLastError();
    }

    @Override
    public void setLastError(String lastError) {
        entity.setLastError(lastError);
    }

    @Override
    public Instant getDeliveredAt() {
        return entity.getDeliveredAt();
    }

    @Override
    public void setDeliveredAt(Instant deliveredAt) {
        entity.setDeliveredAt(deliveredAt);
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
