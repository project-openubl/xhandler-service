package io.github.projectopenubl.xhandler.models;

import io.github.projectopenubl.xhandler.keys.SecretBox;
import java.time.Instant;
import java.util.UUID;

public interface CredentialModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getCompanyId();

    void setCompanyId(UUID companyId);

    /** sol, gre, sire, validity, smtp, lookup */
    String getKind();

    void setKind(String kind);

    /** Non-secret label shown in the API, e.g. the masked user */
    String getLabel();

    void setLabel(String label);

    byte[] getCiphertext();

    void setCiphertext(byte[] ciphertext);

    byte[] getWrappedDataKey();

    void setWrappedDataKey(byte[] wrappedDataKey);

    String getMasterKeyId();

    void setMasterKeyId(String masterKeyId);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    default String context() {
        return "credential:" + getId() + ":" + getTenantId() + ":" + getCompanyId() + ":" + getKind();
    }

    default SecretBox.Sealed sealed() {
        return new SecretBox.Sealed(getCiphertext(), getWrappedDataKey(), getMasterKeyId());
    }

    default void seal(SecretBox.Sealed sealed) {
        setCiphertext(sealed.ciphertext());
        setWrappedDataKey(sealed.wrappedDataKey());
        setMasterKeyId(sealed.masterKeyId());
    }
}
