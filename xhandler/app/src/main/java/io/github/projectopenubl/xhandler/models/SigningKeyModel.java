package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.UUID;

public interface SigningKeyModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getCompanyId();

    void setCompanyId(UUID companyId);

    String getKeyRef();

    void setKeyRef(String keyRef);

    /** X.509, DER */
    byte[] getCertificate();

    void setCertificate(byte[] certificate);

    String getSubject();

    void setSubject(String subject);

    String getIssuer();

    void setIssuer(String issuer);

    String getSerial();

    void setSerial(String serial);

    String getRuc();

    void setRuc(String ruc);

    Instant getValidFrom();

    void setValidFrom(Instant validFrom);

    Instant getValidUntil();

    void setValidUntil(Instant validUntil);

    /** active, replaced or revoked */
    String getStatus();

    void setStatus(String status);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
