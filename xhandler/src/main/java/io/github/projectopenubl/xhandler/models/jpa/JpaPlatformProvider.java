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

    private static final String TENANT_COLUMNS = "id, name, status, partner_id, partner_name, created_by, created_at, "
            + "sending_paused, audit_hold, archive_plan, delete_after, companies";

    @Override
    public List<TenantRow> getTenants(String query, int limit, int offset) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT " + TENANT_COLUMNS + " FROM xhandler.admin_tenants(:q, :l, :o)")
                .setParameter("q", query == null ? "" : query).setParameter("l", limit).setParameter("o", offset).getResultList();
        return rows.stream().map(JpaPlatformProvider::tenant).toList();
    }

    @Override
    public Optional<TenantRow> getTenant(UUID id) {
        return getTenants(id.toString(), 1, 0).stream().filter(t -> t.id().equals(id)).findFirst();
    }

    @Override
    public List<PartnerRow> getPartners() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, name, status, created_at, members, tenants FROM xhandler.admin_partners()")
                .getResultList();
        return rows.stream().map(r -> new PartnerRow((UUID) r[0], (String) r[1], (String) r[2], Times.toInstant(r[3]),
                ((Number) r[4]).longValue(), ((Number) r[5]).longValue())).toList();
    }

    @Override
    public boolean existsPartner(UUID id) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.partner WHERE id = :id").setParameter("id", id).getResultList().isEmpty();
    }

    @Override
    public List<PartnerMemberRow> getPartnerMembers(UUID partnerId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT kind, subject, role, created_at FROM xhandler.partner_member WHERE partner_id = :p ORDER BY created_at, subject")
                .setParameter("p", partnerId).getResultList();
        return rows.stream().map(r -> new PartnerMemberRow((String) r[0], (String) r[1], (String) r[2], Times.toInstant(r[3]))).toList();
    }

    private static TenantRow tenant(Object[] r) {
        return new TenantRow((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4],
                (String) r[5], Times.toInstant(r[6]), Boolean.TRUE.equals(r[7]), Boolean.TRUE.equals(r[8]), Boolean.TRUE.equals(r[9]),
                Times.toInstant(r[10]), ((Number) r[11]).longValue());
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
