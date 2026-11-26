package io.github.projectopenubl.xhandler.jobs;

import io.github.projectopenubl.xhandler.models.JobProvider;
import io.github.projectopenubl.xhandler.models.JobModel;

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

    private final JobProvider jobProvider;

    private final JobStore store;

    public Jobs(JobStore store, JobProvider jobProvider) {
        this.jobProvider = jobProvider;
        this.store = store;
    }

    /** Enqueues a job in the open tenant; it runs after this transaction commits. */
    @Transactional(Transactional.TxType.MANDATORY)
    public JobModel enqueue(TenantScope scope, String name, int priorityClass, Map<String, Object> args, List<String> resources,
            String correlationId) {
        JobModel job = jobProvider.newJob(Ids.newId());
        job.setTenantId(scope.tenantId());
        job.setName(name);
        job.setPriorityClass((short) priorityClass);
        job.setArgs(args);
        job.setReservedResources(resources.toArray(String[]::new));
        job.setCorrelationId(correlationId);
        job.setRunAt(Instant.now());
        job.setCreatedAt(job.getRunAt());
        job.setUpdatedAt(job.getRunAt());
        jobProvider.persist(job);
        store.notifyWakeup();
        return job;
    }

    /**
     * Asks a job to stop: waiting jobs are canceled at once, running ones at their next safe point.
     * One conditional update, never a read-modify-write of the row. Returns the new state, or null.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public String cancel(UUID jobId) {
        return jobProvider.cancelTenantJob(jobId);
    }
}
