package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaJobQueueProvider implements JobQueueProvider {

    private final EntityManager em;

    public JpaJobQueueProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public int unblockJobs() {
        return ((Number) em.createNativeQuery("SELECT xhandler.unblock_jobs()").getSingleResult()).intValue();
    }

    @Override
    public List<JobRef> getClaimableJobs(int limit) {
        List<Object[]> rows = em.createNativeQuery("SELECT id, seq FROM xhandler.claimable_jobs(:n)", Object[].class)
                .setParameter("n", limit)
                .getResultList();
        return rows.stream().map(r -> new JobRef((UUID) r[0], ((Number) r[1]).longValue())).toList();
    }

    @Override
    public Optional<StartedJob> startJob(UUID id, String worker) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT tenant_id, name, attempt, CAST(args AS text), correlation_id FROM xhandler.start_job(:id, :worker)",
                        Object[].class)
                .setParameter("id", id)
                .setParameter("worker", worker)
                .getResultList();
        return rows.stream().findFirst()
                .map(r -> new StartedJob((UUID) r[0], (String) r[1], ((Number) r[2]).intValue(), (String) r[3], (String) r[4]));
    }

    @Override
    public boolean finishJob(UUID id, String worker, String state, String result, String error) {
        Object done = em.createNativeQuery(
                        "SELECT xhandler.finish_job(:id, :worker, :state, CAST(:result AS jsonb), CAST(:error AS jsonb))")
                .setParameter("id", id)
                .setParameter("worker", worker)
                .setParameter("state", state)
                .setParameter("result", result)
                .setParameter("error", error)
                .getSingleResult();
        return Boolean.TRUE.equals(done);
    }

    @Override
    public boolean isCancelRequested(UUID id) {
        return Boolean.TRUE.equals(em.createNativeQuery("SELECT xhandler.job_cancel_requested(:id)")
                .setParameter("id", id)
                .getSingleResult());
    }

    @Override
    public List<RunningJob> getRunningJobs() {
        List<Object[]> rows = em.createNativeQuery("SELECT id, seq, worker FROM xhandler.running_jobs()", Object[].class)
                .getResultList();
        return rows.stream().map(r -> new RunningJob((UUID) r[0], ((Number) r[1]).longValue(), (String) r[2])).toList();
    }

    @Override
    public boolean tryJobLock(long seq) {
        return Boolean.TRUE.equals(em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:seq)")
                .setParameter("seq", seq)
                .getSingleResult());
    }

    @Override
    public boolean tryAdvisoryLock(int namespace, int key) {
        Object locked = em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:ns, :key)")
                .setParameter("ns", namespace)
                .setParameter("key", key)
                .getSingleResult();
        return Boolean.TRUE.equals(locked);
    }

    @Override
    public boolean recoverJob(UUID id) {
        return Boolean.TRUE.equals(em.createNativeQuery("SELECT xhandler.recover_job(:id)")
                .setParameter("id", id)
                .getSingleResult());
    }

    @Override
    public int deleteDeadWorkers(Duration ttl) {
        return ((Number) em.createNativeQuery("SELECT xhandler.delete_dead_workers(make_interval(secs => :ttl))")
                .setParameter("ttl", (double) ttl.toSeconds())
                .getSingleResult()).intValue();
    }

    @Override
    public int dispatchSchedules() {
        return ((Number) em.createNativeQuery("SELECT xhandler.dispatch_schedules()").getSingleResult()).intValue();
    }

    @Override
    public int purgeJobs(Duration age) {
        return ((Number) em.createNativeQuery("SELECT xhandler.purge_jobs(make_interval(secs => :age))")
                .setParameter("age", (double) age.toSeconds())
                .getSingleResult()).intValue();
    }

    @Override
    public void updateBreakers(int minCalls, Duration baseOpen, Duration maxOpen) {
        em.createNativeQuery("SELECT xhandler.update_breakers(:min, make_interval(secs => :base), make_interval(secs => :max))")
                .setParameter("min", minCalls)
                .setParameter("base", (double) baseOpen.toSeconds())
                .setParameter("max", (double) maxOpen.toSeconds())
                .getSingleResult();
    }

    @Override
    public int reconcile(Duration lease, String slots) {
        return ((Number) em.createNativeQuery("SELECT xhandler.reconcile(make_interval(secs => :lease), CAST(:slots AS jsonb))")
                .setParameter("lease", (double) lease.toSeconds())
                .setParameter("slots", slots)
                .getSingleResult()).intValue();
    }

    @Override
    public void enqueuePlatformJob(UUID id, String name, int priorityClass, String args, List<String> resources) {
        em.createNativeQuery("SELECT xhandler.enqueue_platform_job(:id, :name, CAST(:priority AS smallint), CAST(:args AS jsonb), CAST(:resources AS text[]))")
                .setParameter("id", id)
                .setParameter("name", name)
                .setParameter("priority", priorityClass)
                .setParameter("args", args)
                .setParameter("resources", "{" + String.join(",", resources) + "}")
                .getSingleResult();
    }

    @Override
    public void notifyWakeup() {
        em.createNativeQuery("SELECT pg_notify('xhandler_job_wakeup', '')").getSingleResult();
    }

    @Override
    public void notifyReconcile() {
        em.createNativeQuery("SELECT pg_notify('xhandler_reconcile', '')").getSingleResult();
    }

    @Override
    public boolean hasActiveJob(UUID documentId) {
        return !em.createNativeQuery("""
                        SELECT 1 FROM xhandler.job WHERE state IN ('waiting', 'running', 'canceling')
                        AND reserved_resources @> ARRAY[CAST(:resource AS text)]""")
                .setParameter("resource", "document:" + documentId)
                .getResultList().isEmpty();
    }
}
