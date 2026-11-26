package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.JobModel;
import io.github.projectopenubl.xhandler.models.ModelType;
import io.github.projectopenubl.xhandler.models.jpa.entities.JobEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JobAdapter implements JobModel, JpaModel<JobEntity> {

    private final JobEntity entity;

    public JobAdapter(JobEntity entity) {
        this.entity = entity;
    }

    public static JobEntity toEntity(JobModel model) {
        return ((JobAdapter) model).getEntity();
    }

    @Override
    public JobEntity getEntity() {
        return entity;
    }

    @Override
    public UUID getId() {
        return entity.getId();
    }

    @Override
    public ModelType getModelType() {
        return ModelType.JOB;
    }

    @Override
    public Long getSeq() {
        return entity.getSeq();
    }

    @Override
    public void setSeq(Long seq) {
        entity.setSeq(seq);
    }

    @Override
    public UUID getTenantId() {
        return entity.getTenantId();
    }

    @Override
    public void setTenantId(UUID tenantId) {
        entity.setTenantId(tenantId);
    }

    @Override
    public String getName() {
        return entity.getName();
    }

    @Override
    public void setName(String name) {
        entity.setName(name);
    }

    @Override
    public String getState() {
        return entity.getState();
    }

    @Override
    public void setState(String state) {
        entity.setState(state);
    }

    @Override
    public short getPriorityClass() {
        return entity.getPriorityClass();
    }

    @Override
    public void setPriorityClass(short priorityClass) {
        entity.setPriorityClass(priorityClass);
    }

    @Override
    public Instant getRunAt() {
        return entity.getRunAt();
    }

    @Override
    public void setRunAt(Instant runAt) {
        entity.setRunAt(runAt);
    }

    @Override
    public Instant getUnblockedAt() {
        return entity.getUnblockedAt();
    }

    @Override
    public void setUnblockedAt(Instant unblockedAt) {
        entity.setUnblockedAt(unblockedAt);
    }

    @Override
    public String[] getReservedResources() {
        return entity.getReservedResources();
    }

    @Override
    public void setReservedResources(String[] reservedResources) {
        entity.setReservedResources(reservedResources);
    }

    @Override
    public String getCredentialKey() {
        return entity.getCredentialKey();
    }

    @Override
    public void setCredentialKey(String credentialKey) {
        entity.setCredentialKey(credentialKey);
    }

    @Override
    public Integer getCredentialSlots() {
        return entity.getCredentialSlots();
    }

    @Override
    public void setCredentialSlots(Integer credentialSlots) {
        entity.setCredentialSlots(credentialSlots);
    }

    @Override
    public String getEndpoint() {
        return entity.getEndpoint();
    }

    @Override
    public void setEndpoint(String endpoint) {
        entity.setEndpoint(endpoint);
    }

    @Override
    public int getAttempt() {
        return entity.getAttempt();
    }

    @Override
    public void setAttempt(int attempt) {
        entity.setAttempt(attempt);
    }

    @Override
    public Map<String, Object> getArgs() {
        return entity.getArgs();
    }

    @Override
    public void setArgs(Map<String, Object> args) {
        entity.setArgs(args);
    }

    @Override
    public Map<String, Object> getResult() {
        return entity.getResult();
    }

    @Override
    public void setResult(Map<String, Object> result) {
        entity.setResult(result);
    }

    @Override
    public Map<String, Object> getError() {
        return entity.getError();
    }

    @Override
    public void setError(Map<String, Object> error) {
        entity.setError(error);
    }

    @Override
    public String getWorker() {
        return entity.getWorker();
    }

    @Override
    public void setWorker(String worker) {
        entity.setWorker(worker);
    }

    @Override
    public Instant getStartedAt() {
        return entity.getStartedAt();
    }

    @Override
    public void setStartedAt(Instant startedAt) {
        entity.setStartedAt(startedAt);
    }

    @Override
    public Instant getFinishedAt() {
        return entity.getFinishedAt();
    }

    @Override
    public void setFinishedAt(Instant finishedAt) {
        entity.setFinishedAt(finishedAt);
    }

    @Override
    public String getCorrelationId() {
        return entity.getCorrelationId();
    }

    @Override
    public void setCorrelationId(String correlationId) {
        entity.setCorrelationId(correlationId);
    }

    @Override
    public List<String> getCreatedResources() {
        return entity.getCreatedResources();
    }

    @Override
    public void setCreatedResources(List<String> createdResources) {
        entity.setCreatedResources(createdResources);
    }

    @Override
    public Instant getCreatedAt() {
        return entity.getCreatedAt();
    }

    @Override
    public void setCreatedAt(Instant createdAt) {
        entity.setCreatedAt(createdAt);
    }

    @Override
    public Instant getUpdatedAt() {
        return entity.getUpdatedAt();
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        entity.setUpdatedAt(updatedAt);
    }
}
