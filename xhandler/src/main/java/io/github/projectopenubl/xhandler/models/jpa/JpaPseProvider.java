package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PseProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPseProvider implements PseProvider {

    private final EntityManager em;

    public JpaPseProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<AuthorizationRow> getAuthorizations(UUID companyId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, authorized_at, evidence_ref IS NOT NULL, registered_by, approved_at, revoked_at FROM xhandler.pse_authorization
                        WHERE company_id = :c ORDER BY created_at DESC""").setParameter("c", companyId).getResultList();
        return rows.stream().map(r -> new AuthorizationRow((UUID) r[0], Times.toLocalDate(r[1]), (Boolean) r[2], (String) r[3],
                Times.toInstant(r[4]),
                Times.toInstant(r[5]))).toList();
    }

    @Override
    public boolean hasActiveAuthorization(UUID companyId) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.pse_authorization WHERE company_id = :c AND revoked_at IS NULL")
                .setParameter("c", companyId).getResultList().isEmpty();
    }

    @Override
    public boolean hasApprovedAuthorization(UUID companyId) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.pse_authorization WHERE company_id = :c AND revoked_at IS NULL AND approved_at IS NOT NULL")
                .setParameter("c", companyId).getResultList().isEmpty();
    }

    @Override
    public void addAuthorization(UUID id, UUID tenantId, UUID companyId, LocalDate authorizedAt, String evidenceRef, String registeredBy) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.pse_authorization (id, tenant_id, company_id, authorized_at, evidence_ref, registered_by)
                        VALUES (:id, :t, :c, :at, :e, :by)""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("at", authorizedAt)
                .setParameter("e", evidenceRef).setParameter("by", registeredBy).execute();
    }

    @Override
    public int revokeAuthorizations(UUID companyId, String revokedBy) {
        return em.createNativeStatement("""
                        UPDATE xhandler.pse_authorization SET revoked_at = now(), revoked_by = :by WHERE company_id = :c AND revoked_at IS NULL""")
                .setParameter("by", revokedBy).setParameter("c", companyId).execute();
    }

    @Override
    public long countPendingPseDocuments(UUID companyId) {
        return ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM xhandler.document WHERE company_id = :c AND emission_mode = 'pse'
                          AND status IN ('pending_send', 'in_progress', 'unknown', 'voiding')""").setParameter("c", companyId).getSingleResult())
                .longValue();
    }

}
