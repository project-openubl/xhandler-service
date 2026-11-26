package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AuditLogModel extends Model {

    Instant getAt();

    void setAt(Instant at);

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    String getActor();

    void setActor(String actor);

    String getAction();

    void setAction(String action);

    String getObjectType();

    void setObjectType(String objectType);

    String getObjectId();

    void setObjectId(String objectId);

    Map<String, Object> getDetails();

    void setDetails(Map<String, Object> details);
}
