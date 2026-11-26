package io.github.projectopenubl.xhandler.models.jpa.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A tenant's job, as the API shows it (row-level security limits it to the open tenant). Workers
 * never use this entity: they go through {@link JobStore}.
 */
@Entity
@Table(name = "job")
public class JobEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(insertable = false, updatable = false)
    private Long seq;

    private UUID tenantId;
    private String name;
    private String state = "waiting";
    private short priorityClass = 2;
    private Instant runAt;
    private Instant unblockedAt;

    private String[] reservedResources = new String[0];

    private String credentialKey;
    private Integer credentialSlots;
    private String endpoint;
    private int attempt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> args = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> error;

    private String worker;
    private Instant startedAt;
    private Instant finishedAt;
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> createdResources;

    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public short getPriorityClass() {
        return priorityClass;
    }

    public void setPriorityClass(short priorityClass) {
        this.priorityClass = priorityClass;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(Instant runAt) {
        this.runAt = runAt;
    }

    public Instant getUnblockedAt() {
        return unblockedAt;
    }

    public void setUnblockedAt(Instant unblockedAt) {
        this.unblockedAt = unblockedAt;
    }

    public String[] getReservedResources() {
        return reservedResources;
    }

    public void setReservedResources(String[] reservedResources) {
        this.reservedResources = reservedResources;
    }

    public String getCredentialKey() {
        return credentialKey;
    }

    public void setCredentialKey(String credentialKey) {
        this.credentialKey = credentialKey;
    }

    public Integer getCredentialSlots() {
        return credentialSlots;
    }

    public void setCredentialSlots(Integer credentialSlots) {
        this.credentialSlots = credentialSlots;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public void setError(Map<String, Object> error) {
        this.error = error;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public List<String> getCreatedResources() {
        return createdResources;
    }

    public void setCreatedResources(List<String> createdResources) {
        this.createdResources = createdResources;
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
}
