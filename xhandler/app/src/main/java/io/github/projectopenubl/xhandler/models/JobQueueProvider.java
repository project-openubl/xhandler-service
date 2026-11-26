package io.github.projectopenubl.xhandler.models;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The job queue's database operations: the SECURITY DEFINER functions of V4 (they cross tenants),
 * advisory locks and notifications. JSON goes in and out as text; every method joins the caller's
 * transaction.
 */
public interface JobQueueProvider {

    /** A job ready to be claimed. */
    record JobRef(UUID id, long seq) {}

    /** What {@code xhandler.start_job} returns for the job it started; {@code args} as JSON text. */
    record StartedJob(UUID tenantId, String name, int attempt, String args, String correlationId) {}

    record RunningJob(UUID id, long seq, String worker) {}

    int unblockJobs();

    List<JobRef> getClaimableJobs(int limit);

    /** Starts the job for {@code worker}; empty when it could not be started (another worker took it). */
    Optional<StartedJob> startJob(UUID id, String worker);

    /** {@code result} and {@code error} are JSON text or null. */
    boolean finishJob(UUID id, String worker, String state, String result, String error);

    boolean isCancelRequested(UUID id);

    List<RunningJob> getRunningJobs();

    /** {@code pg_try_advisory_xact_lock(seq)}: a job's ownership lock, until the transaction ends. */
    boolean tryJobLock(long seq);

    /** {@code pg_try_advisory_xact_lock(namespace, key)}, until the transaction ends. */
    boolean tryAdvisoryLock(int namespace, int key);

    boolean recoverJob(UUID id);

    int deleteDeadWorkers(Duration ttl);

    int dispatchSchedules();

    int purgeJobs(Duration age);

    void updateBreakers(int minCalls, Duration baseOpen, Duration maxOpen);

    /** {@code slots} as JSON text. */
    int reconcile(Duration lease, String slots);

    /** A platform job (no tenant); {@code args} as JSON text. */
    void enqueuePlatformJob(UUID id, String name, int priorityClass, String args, List<String> resources);

    void notifyWakeup();

    void notifyReconcile();
}
