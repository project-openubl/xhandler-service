package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface JobModel extends Model {

    Long getSeq();

    void setSeq(Long seq);

    UUID getTenantId();

    void setTenantId(UUID tenantId);

    String getName();

    void setName(String name);

    String getState();

    void setState(String state);

    short getPriorityClass();

    void setPriorityClass(short priorityClass);

    Instant getRunAt();

    void setRunAt(Instant runAt);

    Instant getUnblockedAt();

    void setUnblockedAt(Instant unblockedAt);

    String[] getReservedResources();

    void setReservedResources(String[] reservedResources);

    String getCredentialKey();

    void setCredentialKey(String credentialKey);

    Integer getCredentialSlots();

    void setCredentialSlots(Integer credentialSlots);

    String getEndpoint();

    void setEndpoint(String endpoint);

    int getAttempt();

    void setAttempt(int attempt);

    Map<String, Object> getArgs();

    void setArgs(Map<String, Object> args);

    Map<String, Object> getResult();

    void setResult(Map<String, Object> result);

    Map<String, Object> getError();

    void setError(Map<String, Object> error);

    String getWorker();

    void setWorker(String worker);

    Instant getStartedAt();

    void setStartedAt(Instant startedAt);

    Instant getFinishedAt();

    void setFinishedAt(Instant finishedAt);

    String getCorrelationId();

    void setCorrelationId(String correlationId);

    List<String> getCreatedResources();

    void setCreatedResources(List<String> createdResources);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
