package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.BillingProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaBillingProvider implements BillingProvider {

    private final EntityManager em;

    public JpaBillingProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<EntitlementRow> getEntitlement(UUID tenantId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT plan_code, source, synced_at FROM xhandler.entitlement WHERE tenant_id = :t")
                .setParameter("t", tenantId).getResultList();
        return rows.stream().findFirst().map(r -> new EntitlementRow((String) r[0], (String) r[1], Times.toInstant(r[2])));
    }

    @Override
    public UsageCounts countApiDocuments(Instant monthStart, Instant dayStart) {
        Object[] counts = (Object[]) em.createNativeQuery("""
                        SELECT count(*) FILTER (WHERE sunat_env = 'production' AND created_at >= :month),
                               count(*) FILTER (WHERE sunat_env = 'beta' AND created_at >= :day),
                               count(*) FILTER (WHERE sunat_env = 'fake' AND created_at >= :day)
                        FROM xhandler.document WHERE created_at >= :month AND origin = 'api'""")
                .setParameter("month", monthStart).setParameter("day", dayStart).getSingleResult();
        return new UsageCounts(((Number) counts[0]).longValue(), ((Number) counts[1]).longValue(), ((Number) counts[2]).longValue());
    }

    @Override
    public Optional<PlanRow> getPlan(String code) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT code, name, production_per_month, beta_per_day, sandbox_per_day, custom_smtp, webhooks FROM xhandler.plan
                        WHERE code = :c""").setParameter("c", code).getResultList();
        return rows.stream().findFirst().map(JpaBillingProvider::plan);
    }

    @Override
    public PlanRow getFreePlan() {
        return plan((Object[]) em.createNativeQuery("SELECT code, name, production_per_month, beta_per_day, sandbox_per_day, custom_smtp, webhooks "
                + "FROM xhandler.plan WHERE code = 'free'").getSingleResult());
    }

    private static PlanRow plan(Object[] r) {
        return new PlanRow((String) r[0], (String) r[1], (Integer) r[2], (Integer) r[3], (Integer) r[4], (Boolean) r[5], (Boolean) r[6]);
    }

    @Override
    public boolean isKnownPlan(String code) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.plan WHERE code = :c").setParameter("c", code).getResultList().isEmpty();
    }

    @Override
    public void upsertEntitlement(UUID tenantId, String planCode, String source) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.entitlement (tenant_id, plan_code, source, synced_at) VALUES (:t, :c, :s, now())
                        ON CONFLICT (tenant_id) DO UPDATE SET plan_code = excluded.plan_code, source = excluded.source, synced_at = now()""")
                .setParameter("t", tenantId).setParameter("c", planCode).setParameter("s", source).executeUpdate();
    }

    @Override
    public void ensureEntitlement(UUID tenantId) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.entitlement (tenant_id, plan_code) VALUES (:t, 'free') ON CONFLICT (tenant_id) DO NOTHING""")
                .setParameter("t", tenantId).executeUpdate();
    }

    @Override
    public int markQuotaNotified(UUID tenantId, int percent, String period) {
        String column = percent >= 100 ? "notified_100" : "notified_80";
        return em.createNativeQuery("UPDATE xhandler.entitlement SET " + column + " = :p WHERE tenant_id = :t AND "
                        + column + " IS DISTINCT FROM :p")
                .setParameter("p", period).setParameter("t", tenantId).executeUpdate();
    }

    @Override
    public void addUsageEvent(UUID id, UUID tenantId, String type, String propertiesJson) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.usage_event_outbox (id, tenant_id, event_type, properties)
                        VALUES (:id, :tenant, :type, CAST(:properties AS jsonb))""")
                .setParameter("id", id)
                .setParameter("tenant", tenantId)
                .setParameter("type", type)
                .setParameter("properties", propertiesJson)
                .executeUpdate();
    }

    @Override
    public List<UnsentUsageEvent> getUnsentUsageEvents(int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, tenant_id, event_type, quantity, CAST(properties AS text), created_at
                        FROM xhandler.unsent_usage_events(:n)""").setParameter("n", limit).getResultList();
        return rows.stream().map(r -> new UnsentUsageEvent((UUID) r[0], (UUID) r[1], (String) r[2], ((Number) r[3]).intValue(),
                (String) r[4], Times.toInstant(r[5]))).toList();
    }

    @Override
    public void markUsageFailed(UUID id, String error) {
        em.createNativeQuery("SELECT xhandler.mark_usage_failed(:id, :e)")
                .setParameter("id", id).setParameter("e", error).getSingleResult();
    }

    @Override
    public void markUsageSent(List<UUID> ids) {
        em.createNativeQuery("SELECT xhandler.mark_usage_sent(CAST(:ids AS uuid[]))")
                .setParameter("ids", "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}").getSingleResult();
    }

    @Override
    public List<UUID> getEntitlementsToSync() {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT tenant_id FROM xhandler.entitlements_to_sync(interval '15 minutes', 500)")
                .getResultList();
        return ids;
    }

    @Override
    public boolean addLagoInvoice(String lagoInvoiceId, UUID tenantId) {
        int inserted = em.createNativeQuery(
                        "INSERT INTO xhandler.lago_invoice (lago_invoice_id, tenant_id) VALUES (:id, :t) ON CONFLICT DO NOTHING")
                .setParameter("id", lagoInvoiceId).setParameter("t", tenantId).executeUpdate();
        return inserted == 1;
    }

    @Override
    public void setLagoInvoiceDocument(String lagoInvoiceId, UUID documentId) {
        em.createNativeQuery("UPDATE xhandler.lago_invoice SET document_id = :d WHERE lago_invoice_id = :id")
                .setParameter("d", documentId).setParameter("id", lagoInvoiceId).executeUpdate();
    }

    @Override
    public Map<String, String> getPlatformSettings(String a, String b) {
        Map<String, String> settings = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT key, value FROM xhandler.platform_setting WHERE key IN (:a, :b)")
                .setParameter("a", a).setParameter("b", b).getResultList();
        rows.forEach(r -> settings.put((String) r[0], (String) r[1]));
        return settings;
    }
}
