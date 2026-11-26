package io.github.projectopenubl.xhandler.jobs;

import io.github.projectopenubl.xhandler.models.jpa.entities.JobEntity;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates tenant jobs (reports, exports, webhooks...). SUNAT attempts are never created here: the
 * reconciler is their only creator (DESIGN.md §24.1).
 */
@ApplicationScoped
public class Jobs {

    private final JobStore store;

    public Jobs(JobStore store) {
        this.store = store;
    }

    /** Enqueues a job in the open tenant; it runs after this transaction commits. */
    @Transactional(Transactional.TxType.MANDATORY)
    public JobEntity enqueue(TenantScope scope, String name, int priorityClass, Map<String, Object> args, List<String> resources,
            String correlationId) {
        JobEntity job = new JobEntity();
        job.setId(Ids.newId());
        job.setTenantId(scope.tenantId());
        job.setName(name);
        job.setPriorityClass((short) priorityClass);
        job.setArgs(args);
        job.setReservedResources(resources.toArray(String[]::new));
        job.setCorrelationId(correlationId);
        job.setRunAt(Instant.now());
        job.setCreatedAt(job.getRunAt());
        job.setUpdatedAt(job.getRunAt());
        job.persist();
        store.notifyWakeup();
        return job;
    }

    /**
     * Asks a job to stop: waiting jobs are canceled at once, running ones at their next safe point.
     * One conditional update, never a read-modify-write of the row. Returns the new state, or null.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public String cancel(UUID jobId) {
        Object state = JobEntity.getEntityManager().createNativeQuery("SELECT xhandler.cancel_tenant_job(:id)")
                .setParameter("id", jobId)
                .getSingleResult();
        return state == null ? null : state.toString();
    }
}
