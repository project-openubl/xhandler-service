package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.AccessLinkProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaAccessLinkProvider implements AccessLinkProvider {

    private final EntityManager em;

    public JpaAccessLinkProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void addAccessLink(UUID id, UUID tenantId, UUID documentId, String tokenHash, Instant expiresAt) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.access_token (id, tenant_id, document_id, token_hash, expires_at)
                        VALUES (:id, :tenant, :document, :hash, :expires)""")
                .setParameter("id", id)
                .setParameter("tenant", tenantId)
                .setParameter("document", documentId)
                .setParameter("hash", tokenHash)
                .setParameter("expires", expiresAt)
                .execute();
    }

    @Override
    public Optional<LinkedDocument> getLinkedDocument(String tokenHash) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, document_id FROM xhandler.access_token_document(:hash)")
                .setParameter("hash", tokenHash)
                .getResultList();
        return rows.stream().findFirst().map(r -> new LinkedDocument((UUID) r[0], (UUID) r[1]));
    }
}
