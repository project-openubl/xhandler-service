package io.github.projectopenubl.xhandler.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import jakarta.transaction.Transactional;

/**
 * Activities that only one worker runs at a time, under {@code pg_try_advisory_xact_lock(namespace,
 * key)} (DESIGN.md §10.8 rule 5). If the lock is taken, another worker is doing it: skip.
 *
 * <p>Advisory locks use the two-integer form, one namespace per purpose. Job ownership locks use
 * the single-bigint form (the job's {@code seq}), a separate lock space.
 */
@ApplicationScoped
public class Singletons {

    public static final int NAMESPACE = 7001;

    public static final int UNBLOCK = 1;
    public static final int RECONCILE = 2;
    public static final int SCHEDULE_DISPATCH = 3;
    public static final int WORKER_CLEANUP = 4;
    public static final int METRICS = 5;
    public static final int BLOCKED_LIST_IMPORT = 6;
    public static final int RETENTION_PURGE = 7;

    private final JobQueueProvider queue;

    public Singletons(JobQueueProvider queue) {
        this.queue = queue;
    }

    /** Takes the singleton's lock until the current transaction ends; false if another worker holds it. */
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean tryLock(int key) {
        return queue.tryAdvisoryLock(NAMESPACE, key);
    }
}
