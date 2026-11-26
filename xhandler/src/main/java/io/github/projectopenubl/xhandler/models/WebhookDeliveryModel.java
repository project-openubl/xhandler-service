package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface WebhookDeliveryModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getSubscriptionId();

    void setSubscriptionId(UUID subscriptionId);

    UUID getEventId();

    void setEventId(UUID eventId);

    String getEventType();

    void setEventType(String eventType);

    Map<String, Object> getPayload();

    void setPayload(Map<String, Object> payload);

    /** pending, delivered or failed */
    String getStatus();

    void setStatus(String status);

    int getAttempts();

    void setAttempts(int attempts);

    Instant getNextAttemptAt();

    void setNextAttemptAt(Instant nextAttemptAt);

    Integer getLastStatusCode();

    void setLastStatusCode(Integer lastStatusCode);

    String getLastError();

    void setLastError(String lastError);

    Instant getDeliveredAt();

    void setDeliveredAt(Instant deliveredAt);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
