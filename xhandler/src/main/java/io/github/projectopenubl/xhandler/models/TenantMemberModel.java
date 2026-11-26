package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.UUID;

public interface TenantMemberModel extends Model {

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    /** user or client */
    String getKind();

    void setKind(String kind);

    String getSubject();

    void setSubject(String subject);

    String getRole();

    void setRole(String role);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
