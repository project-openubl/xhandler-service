package io.github.projectopenubl.xhandler.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.JobQueueProvider;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The job system's database operations, all through the SECURITY DEFINER functions of V4 (they
 * cross tenants). Stateless; every method runs in the caller's transaction.
 */
@ApplicationScoped
public class JobStore {

    /** A job a worker started. */
    public record Started(UUID id, long seq, UUID tenantId, String name, int attempt, JsonNode args, String correlationId) {}

    public record Candidate(UUID id, long seq) {}

    public record DeadJob(UUID id, long seq, String worker) {}

    private final JobQueueProvider queue;
    private final ObjectMapper json;

    public JobStore(JobQueueProvider queue, ObjectMapper json) {
        this.queue = queue;
        this.json = json;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int unblock() {
        return queue.unblockJobs();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public List<Candidate> claimable(int limit) {
        return queue.getClaimableJobs(limit).stream().map(r -> new Candidate(r.id(), r.seq())).toList();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Started> start(Candidate candidate, String worker) {
        return queue.startJob(candidate.id(), worker).map(r -> new Started(candidate.id(), candidate.seq(), r.tenantId(), r.name(),
                r.attempt(), parse(r.args()), r.correlationId()));
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean finish(UUID id, String worker, String state, Map<String, Object> result, Map<String, Object> error) {
        boolean done = queue.finishJob(id, worker, state, write(result), write(error));
        notifyWakeup();
        return done;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean cancelRequested(UUID id) {
        return queue.isCancelRequested(id);
    }

    /** Every running job; cleanup fails those whose ownership lock it can take. */
    @Transactional(Transactional.TxType.MANDATORY)
    public List<DeadJob> runningJobs() {
        return queue.getRunningJobs().stream().map(r -> new DeadJob(r.id(), r.seq(), r.worker())).toList();
    }

    /** Takes a job's ownership lock until the transaction ends; true means its worker no longer holds it. */
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean tryJobLockForTransaction(long seq) {
        return queue.tryJobLock(seq);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean recover(UUID id) {
        return queue.recoverJob(id);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int deleteDeadWorkers(Duration ttl) {
        return queue.deleteDeadWorkers(ttl);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int dispatchSchedules() {
        return queue.dispatchSchedules();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int purge(Duration age) {
        return queue.purgeJobs(age);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void updateBreakers(int minCalls, Duration baseOpen, Duration maxOpen) {
        queue.updateBreakers(minCalls, baseOpen, maxOpen);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int reconcile(Duration lease, Map<String, Integer> slots) {
        return queue.reconcile(lease, write(slots));
    }

    /** A platform job (no tenant), e.g. from the admin API. */
    @Transactional(Transactional.TxType.MANDATORY)
    public UUID enqueuePlatform(String name, int priorityClass, Map<String, Object> args, List<String> resources) {
        UUID id = io.github.projectopenubl.xhandler.utils.Ids.newId();
        queue.enqueuePlatformJob(id, name, priorityClass, write(args), resources);
        return id;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void notifyWakeup() {
        queue.notifyWakeup();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void notifyReconcile() {
        queue.notifyReconcile();
    }

    private String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
