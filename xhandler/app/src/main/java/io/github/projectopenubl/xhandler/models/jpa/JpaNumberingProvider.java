package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.NumberingProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaNumberingProvider implements NumberingProvider {

    private final EntityManager em;

    public JpaNumberingProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Long> nextSeriesNumber(UUID companyId, String sunatEnv, String tipoComprobante, String serie) {
        List<?> next = em.createNativeQuery("""
                        UPDATE xhandler.series SET next_number = next_number + 1, updated_at = now()
                        WHERE company_id = :company AND sunat_env = :env AND tipo_comprobante = :tipo AND serie = :serie AND active
                        RETURNING next_number - 1""")
                .setParameter("company", companyId)
                .setParameter("env", sunatEnv)
                .setParameter("tipo", tipoComprobante)
                .setParameter("serie", serie)
                .getResultList();
        return next.stream().findFirst().map(n -> ((Number) n).longValue());
    }

    @Override
    public Optional<SeriesCounter> getSeriesCounterForUpdate(UUID companyId, String sunatEnv, String tipoComprobante, String serie) {
        @SuppressWarnings("unchecked")
        List<Object[]> existing = em.createNativeQuery("""
                        SELECT next_number, active FROM xhandler.series
                        WHERE company_id = :c AND sunat_env = :env AND tipo_comprobante = :tipo AND serie = :serie FOR UPDATE""")
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("tipo", tipoComprobante).setParameter("serie", serie)
                .getResultList();
        return existing.stream().findFirst().map(r -> new SeriesCounter(((Number) r[0]).longValue(), Boolean.TRUE.equals(r[1])));
    }

    @Override
    public void upsertSeriesCounter(UUID id, UUID tenantId, UUID companyId, String sunatEnv, String tipoComprobante, String serie,
            long nextNumber) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.series (id, tenant_id, company_id, sunat_env, tipo_comprobante, serie, next_number)
                        VALUES (:id, :t, :c, :env, :tipo, :serie, :next)
                        ON CONFLICT (company_id, sunat_env, tipo_comprobante, serie)
                        DO UPDATE SET next_number = greatest(series.next_number, excluded.next_number), updated_at = now()""")
                .setParameter("id", id).setParameter("t", tenantId)
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("tipo", tipoComprobante)
                .setParameter("serie", serie).setParameter("next", nextNumber).executeUpdate();
    }

    @Override
    public int nextDailyNumber(UUID tenantId, UUID companyId, String sunatEnv, String kind, LocalDate day) {
        Object next = em.createNativeQuery("""
                        INSERT INTO xhandler.daily_counter (tenant_id, company_id, sunat_env, kind, day, next_number)
                        VALUES (:tenant, :company, :env, :kind, :day, 2)
                        ON CONFLICT (company_id, sunat_env, kind, day) DO UPDATE SET next_number = daily_counter.next_number + 1
                        RETURNING next_number - 1""")
                .setParameter("tenant", tenantId)
                .setParameter("company", companyId)
                .setParameter("env", sunatEnv)
                .setParameter("kind", kind)
                .setParameter("day", day)
                .getSingleResult();
        return ((Number) next).intValue();
    }

    @Override
    public void setLockTimeout(String timeout) {
        em.createNativeQuery("SELECT set_config('lock_timeout', :timeout, true)").setParameter("timeout", timeout).getSingleResult();
    }
}
