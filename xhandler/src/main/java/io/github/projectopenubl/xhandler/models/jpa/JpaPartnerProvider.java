package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PartnerProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPartnerProvider implements PartnerProvider {

    private final EntityManager em;

    public JpaPartnerProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<PartnerMembership> getPartnersOf(String kind, String subject) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, name, role FROM xhandler.partners_of(:k, :s)")
                .setParameter("k", kind).setParameter("s", subject).getResultList();
        return rows.stream().map(r -> new PartnerMembership((UUID) r[0], (String) r[1], (String) r[2])).toList();
    }

    @Override
    public void createPartnerTenant(UUID partnerId, UUID id, String name, String by, String ownerKind, String ownerSubject) {
        em.createNativeQuery("SELECT xhandler.create_partner_tenant(:p, :id, :name, :by, :kind, :subject)")
                .setParameter("p", partnerId).setParameter("id", id).setParameter("name", name)
                .setParameter("by", by)
                .setParameter("kind", ownerKind)
                .setParameter("subject", ownerSubject)
                .getSingleResult();
    }

    @Override
    public List<PartnerTenant> getPartnerTenants(UUID partnerId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, name, status FROM xhandler.partner_tenants(:p)")
                .setParameter("p", partnerId).getResultList();
        return rows.stream().map(r -> new PartnerTenant((UUID) r[0], (String) r[1], (String) r[2])).toList();
    }

    @Override
    public void setPartnerBranding(UUID partnerId, String brandingJson) {
        em.createNativeQuery("SELECT xhandler.set_partner_branding(:p, CAST(:b AS jsonb))")
                .setParameter("b", brandingJson).setParameter("p", partnerId).getSingleResult();
    }

    @Override
    public void setPartnerLogo(UUID partnerId, String ref) {
        em.createNativeQuery("SELECT xhandler.set_partner_logo(:p, :ref)")
                .setParameter("p", partnerId).setParameter("ref", ref).getSingleResult();
    }

    @Override
    public void clearPartnerLogo(UUID partnerId) {
        em.createNativeQuery("SELECT xhandler.set_partner_logo(:p, CAST(NULL AS text))")
                .setParameter("p", partnerId).getSingleResult();
    }

    @Override
    public PartnerBranding getPartnerBranding(UUID partnerId) {
        Object[] row = (Object[]) em.createNativeQuery("SELECT CAST(branding AS text), logo_ref IS NOT NULL FROM xhandler.partner WHERE id = :p")
                .setParameter("p", partnerId).getSingleResult();
        return new PartnerBranding((String) row[0], (Boolean) row[1]);
    }
}
