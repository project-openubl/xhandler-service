package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.MembershipProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaMembershipProvider implements MembershipProvider {

    private final EntityManager em;

    public JpaMembershipProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<String> getMemberRole(UUID tenantId, String kind, String subject) {
        Object role = em.createNativeQuery("SELECT xhandler.member_role(:tenant, :kind, :subject)")
                .setParameter("tenant", tenantId)
                .setParameter("kind", kind)
                .setParameter("subject", subject)
                .getSingleResult();
        return Optional.ofNullable(role).map(Object::toString);
    }

    @Override
    public Optional<String> getPartnerRole(UUID tenantId, String kind, String subject) {
        Object role = em.createNativeQuery("SELECT xhandler.partner_role(:tenant, :kind, :subject)")
                .setParameter("tenant", tenantId)
                .setParameter("kind", kind)
                .setParameter("subject", subject)
                .getSingleResult();
        return Optional.ofNullable(role).map(Object::toString);
    }

    @Override
    public boolean hasSupportSession(UUID tenantId, String operator) {
        Object active = em.createNativeQuery("SELECT xhandler.has_support_session(:tenant, :operator)")
                .setParameter("tenant", tenantId)
                .setParameter("operator", operator)
                .getSingleResult();
        return Boolean.TRUE.equals(active);
    }

    @Override
    public void setTenant(UUID tenantId) {
        em.createNativeQuery("SELECT set_config('app.tenant_id', :tenant, true)")
                .setParameter("tenant", tenantId.toString())
                .getSingleResult();
    }

    @Override
    public List<MemberTenant> getTenantsOf(String kind, String subject) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, name, role FROM xhandler.tenants_of(:kind, :subject)")
                .setParameter("kind", kind)
                .setParameter("subject", subject)
                .getResultList();
        return rows.stream().map(r -> new MemberTenant((UUID) r[0], (String) r[1], (String) r[2])).toList();
    }

    @Override
    public long countFreeTenantsCreatedBy(String subject) {
        Number created = (Number) em.createNativeQuery("SELECT xhandler.free_tenants_created_by(:subject)")
                .setParameter("subject", subject)
                .getSingleResult();
        return created.longValue();
    }
}
