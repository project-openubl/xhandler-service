package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.MaintenanceProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaMaintenanceProvider implements MaintenanceProvider {

    private final EntityManager em;

    public JpaMaintenanceProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void ensurePartitions(LocalDate day) {
        em.createNativeQuery("SELECT xhandler.ensure_partitions(:day)").setParameter("day", day).getSingleResult();
    }

    @Override
    public int purgeIdempotencyKeys() {
        return ((Number) em.createNativeQuery("SELECT xhandler.purge_idempotency_keys(interval '24 hours')").getSingleResult()).intValue();
    }

    @Override
    public int deleteOldRateBuckets() {
        return em.createNativeQuery("DELETE FROM xhandler.public_lookup_counter WHERE bucket < now() - interval '1 day'").executeUpdate();
    }

    @Override
    public List<ExpiredDocument> getExpiredDocuments() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, document_id FROM xhandler.expired_documents(500)").getResultList();
        return rows.stream().map(r -> new ExpiredDocument((UUID) r[0], (UUID) r[1])).toList();
    }

    @Override
    public List<String> purgeDocuments(UUID tenantId, List<UUID> documentIds) {
        @SuppressWarnings("unchecked")
        List<String> keys = em.createNativeQuery("SELECT * FROM xhandler.purge_documents(:t, cast(:ids AS uuid[]))")
                .setParameter("t", tenantId).setParameter("ids", documentIds.toArray(UUID[]::new)).getResultList();
        return keys;
    }

    @Override
    public int purgeDeletedDrafts(int days, int limit) {
        return ((Number) em.createNativeQuery("SELECT xhandler.purge_deleted_drafts(:days, :limit)")
                .setParameter("days", days).setParameter("limit", limit).getSingleResult()).intValue();
    }
}
