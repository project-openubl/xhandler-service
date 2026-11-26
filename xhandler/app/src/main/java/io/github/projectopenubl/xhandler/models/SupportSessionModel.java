package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.UUID;

public interface SupportSessionModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    String getOperator();

    void setOperator(String operator);

    String getReason();

    void setReason(String reason);

    Instant getRequestedAt();

    void setRequestedAt(Instant requestedAt);

    String getApprovedBy();

    void setApprovedBy(String approvedBy);

    Instant getApprovedAt();

    void setApprovedAt(Instant approvedAt);

    Instant getExpiresAt();

    void setExpiresAt(Instant expiresAt);

    Instant getRevokedAt();

    void setRevokedAt(Instant revokedAt);
}
