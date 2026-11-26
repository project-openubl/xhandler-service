package io.github.projectopenubl.xhandler.models;

import io.github.projectopenubl.xhandler.utils.Ids;
import java.time.Instant;
import java.util.UUID;

public interface DocumentEventModel extends Model {

    Instant getAt();

    void setAt(Instant at);

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    UUID getDocumentId();

    void setDocumentId(UUID documentId);

    String getStatus();

    void setStatus(String status);

    Integer getCode();

    void setCode(Integer code);

    String getMessage();

    void setMessage(String message);

    String getActor();

    void setActor(String actor);

    String getCorrelationId();

    void setCorrelationId(String correlationId);
}
