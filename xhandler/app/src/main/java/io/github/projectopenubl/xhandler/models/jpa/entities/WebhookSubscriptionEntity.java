package io.github.projectopenubl.xhandler.models.jpa.entities;

import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A tenant's webhook endpoint. Its signing secret is sealed like any other secret (§6). */
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscriptionEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    private UUID tenantId;
    private String url;

    private String[] events;

    private byte[] ciphertext;
    private byte[] wrappedDataKey;
    private String masterKeyId;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public String context() {
        return "webhook:" + id + ":" + tenantId;
    }

    public SecretBox.Sealed sealed() {
        return new SecretBox.Sealed(ciphertext, wrappedDataKey, masterKeyId);
    }

    public void seal(SecretBox.Sealed sealed) {
        ciphertext = sealed.ciphertext();
        wrappedDataKey = sealed.wrappedDataKey();
        masterKeyId = sealed.masterKeyId();
    }

    /** {@code *} subscribes to everything; {@code document.*} to every document event. */
    public boolean wants(String type) {
        for (String pattern : events) {
            if (pattern.equals("*") || pattern.equals(type) || (pattern.endsWith(".*") && type.startsWith(pattern.substring(0, pattern.length() - 1)))) {
                return true;
            }
        }
        return false;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String[] getEvents() {
        return events;
    }

    public void setEvents(String[] events) {
        this.events = events;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(byte[] ciphertext) {
        this.ciphertext = ciphertext;
    }

    public byte[] getWrappedDataKey() {
        return wrappedDataKey;
    }

    public void setWrappedDataKey(byte[] wrappedDataKey) {
        this.wrappedDataKey = wrappedDataKey;
    }

    public String getMasterKeyId() {
        return masterKeyId;
    }

    public void setMasterKeyId(String masterKeyId) {
        this.masterKeyId = masterKeyId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
