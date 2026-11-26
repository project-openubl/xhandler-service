package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.MetricsProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaMetricsProvider implements MetricsProvider {

    private final EntityManager em;

    public JpaMetricsProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Double> getSnapshotValue(String name) {
        List<?> rows = em.createNativeQuery(
                "SELECT value FROM xhandler.ops_snapshot WHERE name = :n AND at > now() - interval '5 minutes'").setParameter("n", name)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(((Number) rows.get(0)).doubleValue());
    }

    @Override
    public DocumentHealth getDocumentHealth() {
        Object[] d = (Object[]) em.createNativeQuery("SELECT exception, unknown_stale, past_deadline, pending FROM xhandler.document_health()")
                .getSingleResult();
        return new DocumentHealth(number(d[0]), number(d[1]), number(d[2]), number(d[3]));
    }

    @Override
    public JobBacklog getJobBacklog() {
        Object[] j = (Object[]) em.createNativeQuery("SELECT waiting, oldest_seconds FROM xhandler.job_backlog()").getSingleResult();
        return new JobBacklog(number(j[0]), number(j[1]));
    }

    @Override
    public void saveSnapshotValue(String name, double value) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.ops_snapshot (name, value, at) VALUES (:n, :v, now())
                        ON CONFLICT (name) DO UPDATE SET value = excluded.value, at = excluded.at""")
                .setParameter("n", name).setParameter("v", value)
                .executeUpdate();
    }

    private static double number(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }
}
