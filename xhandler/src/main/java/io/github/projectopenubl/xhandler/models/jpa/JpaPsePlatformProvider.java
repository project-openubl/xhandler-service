package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PsePlatformProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPsePlatformProvider implements PsePlatformProvider {

    private final EntityManager em;

    public JpaPsePlatformProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<PendingAuthorization> getPendingAuthorizations() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, tenant_id, company_id, ruc, razon_social, authorized_at, evidence_ref IS NOT NULL, created_at
                        FROM xhandler.pending_pse_authorizations()""").getResultList();
        return rows.stream().map(r -> new PendingAuthorization((UUID) r[0], (UUID) r[1], (UUID) r[2], (String) r[3], (String) r[4],
                String.valueOf(r[5]), (Boolean) r[6])).toList();
    }

    @Override
    public Optional<String> getAuthorizationEvidenceRef(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, evidence_ref FROM xhandler.pse_authorization_evidence(:id)")
                .setParameter("id", id).getResultList();
        if (rows.isEmpty() || rows.get(0)[1] == null) {
            return Optional.empty();
        }
        return Optional.of((String) rows.get(0)[1]);
    }

    @Override
    public boolean approveAuthorization(UUID id, String by) {
        List<?> tenant = em.createNativeQuery("SELECT xhandler.approve_pse_authorization(:id, :by)").setParameter("id", id).setParameter("by", by)
                .getResultList();
        return !(tenant.isEmpty() || tenant.get(0) == null);
    }

    @Override
    public Outcomes getOutcomes(Instant from, Instant to) {
        Object[] r = (Object[]) em.createNativeQuery("SELECT accepted, rejected FROM xhandler.pse_outcomes(:f, :t)")
                .setParameter("f", from)
                .setParameter("t", to)
                .getSingleResult();
        return new Outcomes(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
    }

    @Override
    public Optional<Double> getLatestReportRate() {
        List<?> rates = em.createNativeQuery("SELECT rate FROM xhandler.pse_report ORDER BY period DESC LIMIT 1").getResultList();
        return rates.isEmpty() ? Optional.empty() : Optional.of(((Number) rates.get(0)).doubleValue());
    }

    @Override
    public void saveReport(String period, long accepted, long rejected, BigDecimal rate) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.pse_report (period, accepted, rejected, rate) VALUES (:p, :a, :r, :rate)
                        ON CONFLICT (period) DO UPDATE SET accepted = excluded.accepted, rejected = excluded.rejected,
                            rate = excluded.rate, computed_at = now()""")
                .setParameter("p", period).setParameter("a", accepted).setParameter("r", rejected)
                .setParameter("rate", rate).execute();
    }
}
