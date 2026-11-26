package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.IdempotencyProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaIdempotencyProvider implements IdempotencyProvider {

    private final EntityManager em;

    public JpaIdempotencyProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean insertKey(UUID tenantId, String key, String requestHash, UUID claim) {
        int inserted = em.createNativeStatement("""
                        INSERT INTO xhandler.idempotency_key (tenant_id, key, request_hash, claim) VALUES (:tenant, :key, :hash, :claim)
                        ON CONFLICT (tenant_id, key) DO NOTHING""")
                .setParameter("tenant", tenantId)
                .setParameter("key", key)
                .setParameter("hash", requestHash)
                .setParameter("claim", claim)
                .execute();
        return inserted == 1;
    }

    @Override
    public Optional<IdempotencyKey> getKeyForUpdate(UUID tenantId, String key) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT request_hash, status_code, CAST(response AS text), created_at < now() - interval '2 minutes'
                        FROM xhandler.idempotency_key WHERE tenant_id = :tenant AND key = :key FOR UPDATE""")
                .setParameter("tenant", tenantId)
                .setParameter("key", key)
                .getResultList();
        return rows.stream().findFirst().map(row -> new IdempotencyKey((String) row[0],
                row[1] == null ? null : ((Number) row[1]).intValue(), (String) row[2], Boolean.TRUE.equals(row[3])));
    }

    @Override
    public void takeOverKey(UUID tenantId, String key, UUID claim) {
        em.createNativeStatement("UPDATE xhandler.idempotency_key SET created_at = now(), claim = :claim WHERE tenant_id = :tenant AND key = :key")
                .setParameter("claim", claim)
                .setParameter("tenant", tenantId)
                .setParameter("key", key)
                .execute();
    }

    @Override
    public int completeKey(UUID tenantId, String key, UUID claim, int status, String body) {
        return em.createNativeStatement("""
                        UPDATE xhandler.idempotency_key SET status_code = :status, response = CAST(:body AS jsonb), completed_at = now()
                        WHERE tenant_id = :tenant AND key = :key AND claim = :claim AND status_code IS NULL""")
                .setParameter("status", status)
                .setParameter("body", body)
                .setParameter("tenant", tenantId)
                .setParameter("key", key)
                .setParameter("claim", claim)
                .execute();
    }

    @Override
    public void deleteKey(UUID tenantId, String key, UUID claim) {
        em.createNativeStatement(
                        "DELETE FROM xhandler.idempotency_key WHERE tenant_id = :tenant AND key = :key AND claim = :claim AND status_code IS NULL")
                .setParameter("tenant", tenantId)
                .setParameter("key", key)
                .setParameter("claim", claim)
                .execute();
    }
}
