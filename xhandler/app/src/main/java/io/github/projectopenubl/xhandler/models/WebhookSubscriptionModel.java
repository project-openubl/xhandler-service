package io.github.projectopenubl.xhandler.models;

import io.github.projectopenubl.xhandler.keys.SecretBox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookSubscriptionModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    String getUrl();

    void setUrl(String url);

    String[] getEvents();

    void setEvents(String[] events);

    byte[] getCiphertext();

    void setCiphertext(byte[] ciphertext);

    byte[] getWrappedDataKey();

    void setWrappedDataKey(byte[] wrappedDataKey);

    String getMasterKeyId();

    void setMasterKeyId(String masterKeyId);

    boolean isActive();

    void setActive(boolean active);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    Instant getDeletedAt();

    void setDeletedAt(Instant deletedAt);

    default String context() {
        return "webhook:" + getId() + ":" + getTenantId();
    }

    default SecretBox.Sealed sealed() {
        return new SecretBox.Sealed(getCiphertext(), getWrappedDataKey(), getMasterKeyId());
    }

    default void seal(SecretBox.Sealed sealed) {
        setCiphertext(sealed.ciphertext());
        setWrappedDataKey(sealed.wrappedDataKey());
        setMasterKeyId(sealed.masterKeyId());
    }

    default boolean wants(String type) {
        for (String pattern : getEvents()) {
            if (pattern.equals("*") || pattern.equals(type) || (pattern.endsWith(".*") && type.startsWith(pattern.substring(0, pattern.length() - 1)))) {
                return true;
            }
        }
        return false;
    }
}
