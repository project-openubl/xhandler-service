package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.StatusProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaStatusProvider implements StatusProvider {

    private final EntityManager em;

    public JpaStatusProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<WorkerCount> getOnlineWorkers() {
        @SuppressWarnings("unchecked")
        List<Object[]> workers = em.createNativeQuery(
                        "SELECT role, count(*) FROM xhandler.worker WHERE last_heartbeat > now() - interval '30 seconds' GROUP BY role ORDER BY role")
                .getResultList();
        return workers.stream().map(w -> new WorkerCount((String) w[0], ((Number) w[1]).longValue())).toList();
    }

    @Override
    public List<QueueStats> getQueueStats() {
        @SuppressWarnings("unchecked")
        List<Object[]> queues = em.createNativeQuery(
                        "SELECT priority_class, waiting, unblocked, running, oldest_unblocked FROM xhandler.queue_stats() ORDER BY priority_class")
                .getResultList();
        return queues.stream().map(q -> new QueueStats(((Number) q[0]).intValue(), ((Number) q[1]).longValue(), ((Number) q[2]).longValue(),
                ((Number) q[3]).longValue(), Times.toInstant(q[4]))).toList();
    }

    @Override
    public List<EndpointState> getSunatEndpoints() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, breaker_state, breaker_until, paused FROM xhandler.sunat_endpoint ORDER BY id""")
                .getResultList();
        return rows.stream().map(r -> new EndpointState((String) r[0], (String) r[1], Times.toInstant(r[2]), (Boolean) r[3])).toList();
    }
}
