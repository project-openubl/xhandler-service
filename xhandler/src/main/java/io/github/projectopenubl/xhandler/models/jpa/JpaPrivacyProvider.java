package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PrivacyProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPrivacyProvider implements PrivacyProvider {

    private final EntityManager em;

    public JpaPrivacyProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public int setAuditHold(UUID tenantId, boolean value) {
        return updateTenant(tenantId, "audit_hold = :v", value);
    }

    @Override
    public int setArchivePlan(UUID tenantId, boolean value) {
        return updateTenant(tenantId, "archive_plan = :v", value);
    }

    private int updateTenant(UUID tenantId, String set, boolean value) {
        return em.createNativeStatement("UPDATE xhandler.tenant SET " + set + ", updated_at = now() WHERE id = :id AND deleted_at IS NULL")
                .setParameter("v", value).setParameter("id", tenantId).execute();
    }

    @Override
    public void scheduleExitedTenantDeletion(UUID tenantId) {
        em.createNativeStatement("""
                        UPDATE xhandler.tenant SET delete_after = greatest(exit_requested_at + interval '730 days', now() + interval '30 days')
                        WHERE id = :id AND status = 'exited' AND delete_after IS NULL AND deleted_at IS NULL""")
                .setParameter("id", tenantId).execute();
    }

    @Override
    public int cancelExit(UUID tenantId) {
        return em.createNativeStatement("""
                        UPDATE xhandler.tenant SET exit_requested_at = NULL, delete_after = NULL, status = 'active', updated_at = now()
                        WHERE id = :id AND status = 'exited' AND deleted_at IS NULL""").setParameter("id", tenantId).execute();
    }
}
