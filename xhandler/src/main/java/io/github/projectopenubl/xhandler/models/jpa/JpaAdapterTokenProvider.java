package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.AdapterTokenProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaAdapterTokenProvider implements AdapterTokenProvider {

    private final EntityManager em;

    public JpaAdapterTokenProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void addNubefactToken(UUID id, UUID tenantId, UUID companyId, String tokenHash, String label, String createdBy) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.adapter_token (id, tenant_id, company_id, kind, token_hash, label, created_by)
                        VALUES (:id, :t, :c, 'nubefact', :h, :l, :by)""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("h", tokenHash)
                .setParameter("l", label).setParameter("by", createdBy).execute();
    }

    @Override
    public List<AdapterToken> getAdapterTokens(UUID companyId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, kind, label, created_by, created_at, revoked_at FROM xhandler.adapter_token WHERE company_id = :c ORDER BY created_at DESC""")
                .setParameter("c", companyId).getResultList();
        return rows.stream().map(r -> new AdapterToken((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                Times.toInstant(r[4]),
                Times.toInstant(r[5]))).toList();
    }

    @Override
    public boolean revokeAdapterToken(UUID tokenId, UUID companyId) {
        int revoked = em.createNativeStatement("UPDATE xhandler.adapter_token SET revoked_at = now() WHERE id = :id AND company_id = :c AND revoked_at IS NULL")
                .setParameter("id", tokenId).setParameter("c", companyId).execute();
        return revoked != 0;
    }

    @Override
    public Optional<TokenCompany> getNubefactTokenCompany(String tokenHash) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, company_id, label, token_id FROM xhandler.adapter_token_company_v2(:h, 'nubefact')")
                .setParameter("h", tokenHash).getResultList();
        return rows.stream().findFirst().map(r -> new TokenCompany((UUID) r[0], (UUID) r[1], (String) r[2], (UUID) r[3]));
    }
}
