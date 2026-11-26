package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PlatformProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPlatformProvider implements PlatformProvider {

    private final EntityManager em;

    public JpaPlatformProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<UUID> getDocumentTenant(UUID documentId) {
        Object tenant = em.createNativeQuery("SELECT xhandler.document_tenant(:id)").setParameter("id", documentId).getSingleResult();
        return Optional.ofNullable((UUID) tenant);
    }


    @Override
    public void createPartner(UUID id, String name) {
        em.createNativeQuery("SELECT xhandler.create_partner(:id, :n)").setParameter("id", id).setParameter("n", name)
                .getSingleResult();
    }

    @Override
    public boolean setPartnerMember(UUID partnerId, String kind, String subject, String role) {
        Object set = em.createNativeQuery("SELECT xhandler.set_partner_member(:p, :k, :s, :r)")
                .setParameter("p", partnerId).setParameter("k", kind).setParameter("s", subject).setParameter("r", role).getSingleResult();
        return Boolean.TRUE.equals(set);
    }

    @Override
    public boolean removePartnerMember(UUID partnerId, String kind, String subject) {
        Object removed = em.createNativeQuery("SELECT xhandler.remove_partner_member(:p, :k, :s)")
                .setParameter("p", partnerId).setParameter("k", kind).setParameter("s", subject).getSingleResult();
        return Boolean.TRUE.equals(removed);
    }

    @Override
    public void setPlatformSetting(String key, String value) {
        em.createNativeStatement("INSERT INTO xhandler.platform_setting (key, value) VALUES (:k, :v) "
                        + "ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = now()")
                .setParameter("k", key).setParameter("v", value).execute();
    }

    @Override
    public List<EndpointRow> getEndpoints() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, breaker_state, breaker_until, concurrency_cap, paused FROM xhandler.sunat_endpoint ORDER BY id")
                .getResultList();
        return rows.stream().map(JpaPlatformProvider::endpoint).toList();
    }

    @Override
    public EndpointRow getEndpoint(String id) {
        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT id, breaker_state, breaker_until, concurrency_cap, paused FROM xhandler.sunat_endpoint WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        return endpoint(row);
    }

    @Override
    public int setEndpointPaused(String id, boolean paused) {
        return updateEndpoint(id, "paused = " + paused);
    }

    @Override
    public int setEndpointConcurrencyCap(String id, int concurrencyCap) {
        return updateEndpoint(id, "concurrency_cap = " + concurrencyCap);
    }

    private int updateEndpoint(String id, String assignment) {
        return em.createNativeStatement("UPDATE xhandler.sunat_endpoint SET " + assignment + ", updated_at = now() WHERE id = :id")
                .setParameter("id", id)
                .execute();
    }

    @Override
    public Optional<String> cancelJob(UUID id) {
        Object state = em.createNativeQuery("SELECT xhandler.cancel_job_any(:id)").setParameter("id", id).getSingleResult();
        return Optional.ofNullable(state).map(Object::toString);
    }

    @Override
    public List<MigrationRow> getMigrations() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT version, description, success, installed_on FROM xhandler.migration_history()").getResultList();
        return rows.stream().map(r -> new MigrationRow((String) r[0], (String) r[1], Boolean.TRUE.equals(r[2]), (LocalDateTime) r[3]))
                .toList();
    }


    @Override
    public int requeueStuckDocuments() {
        return ((Number) em.createNativeQuery("SELECT xhandler.requeue_stuck_documents()").getSingleResult()).intValue();
    }

    @Override
    public Optional<JobSummary> getJobSummary(UUID id) {
        List<?> rows = em.createNativeQuery("SELECT name, state, worker, CAST(error AS text) FROM xhandler.job_summary(:id)")
                .setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        return Optional.of(new JobSummary((String) r[0], (String) r[1], (String) r[2], (String) r[3]));
    }


    private static EndpointRow endpoint(Object[] r) {
        return new EndpointRow((String) r[0], (String) r[1], Times.toInstant(r[2]), ((Number) r[3]).intValue(), (Boolean) r[4]);
    }
}
