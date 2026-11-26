package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.ConformityProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaConformityProvider implements ConformityProvider {

    private static final String SELECT = """
            SELECT c.document_id, d.serie, d.numero, c.buyer_ruc, c.state, c.accepted_at, c.window_ends_at, c.checked_at, c.check_detail,
                   c.resolved_at, d.total, d.moneda, d.fecha_emision, d.customer_nombre
            FROM xhandler.conformity c JOIN xhandler.document d ON d.id = c.document_id""";

    private final EntityManager em;

    public JpaConformityProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<ConformityRow> getDocumentConformity(UUID documentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(SELECT + " WHERE c.document_id = :d").setParameter("d", documentId).getResultList();
        return rows.stream().findFirst().map(JpaConformityProvider::row);
    }

    @Override
    public List<ConformityRow> getConformities(UUID companyId, String state, int limit, int offset) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(SELECT + """
                         WHERE c.company_id = :c AND (cast(:s AS text) IS NULL OR c.state = :s)
                        ORDER BY c.accepted_at DESC, c.document_id DESC LIMIT :limit OFFSET :offset""")
                .setParameter("c", companyId).setParameter("s", state).setParameter("limit", limit).setParameter("offset", offset)
                .getResultList();
        return rows.stream().map(JpaConformityProvider::row).toList();
    }

    @Override
    public void addConformity(UUID id, UUID tenantId, UUID companyId, UUID documentId, String buyerRuc, Instant acceptedAt,
            Instant windowEndsAt) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.conformity (id, tenant_id, company_id, document_id, buyer_ruc, accepted_at, window_ends_at)
                        VALUES (:id, :t, :c, :d, :buyer, :at, :ends) ON CONFLICT (document_id) DO NOTHING""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("d", documentId)
                .setParameter("buyer", buyerRuc).setParameter("at", acceptedAt).setParameter("ends", windowEndsAt).execute();
    }

    @Override
    public void closeConformity(UUID documentId) {
        em.createNativeStatement("UPDATE xhandler.conformity SET state = 'closed', resolved_at = now() WHERE document_id = :d AND state = 'pending'")
                .setParameter("d", documentId).execute();
    }

    @Override
    public List<DueConformity> getConformitiesToCheck() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, conformity_id FROM xhandler.conformities_to_check(100)").getResultList();
        return rows.stream().map(r -> new DueConformity((UUID) r[0], (UUID) r[1])).toList();
    }

    @Override
    public ConformityCheck getConformityCheck(UUID id) {
        Object[] c = (Object[]) em.createNativeQuery("SELECT document_id, window_ends_at, reminded_at FROM xhandler.conformity WHERE id = :id")
                .setParameter("id", id).getSingleResult();
        return new ConformityCheck((UUID) c[0], Times.toInstant(c[1]), Times.toInstant(c[2]));
    }

    @Override
    public int resolveConformity(UUID id, String state) {
        return em.createNativeStatement("""
                        UPDATE xhandler.conformity SET state = :s, resolved_at = now(), checked_at = now(), check_detail = NULL
                        WHERE id = :id AND state = 'pending'""").setParameter("s", state).setParameter("id", id).execute();
    }

    @Override
    public int markReminded(UUID id) {
        return em.createNativeStatement("UPDATE xhandler.conformity SET reminded_at = now() WHERE id = :id AND reminded_at IS NULL")
                .setParameter("id", id).execute();
    }

    @Override
    public void stampConformity(UUID id, String detail) {
        em.createNativeStatement("UPDATE xhandler.conformity SET checked_at = now(), check_detail = :d WHERE id = :id")
                .setParameter("d", detail).setParameter("id", id).execute();
    }

    private static ConformityRow row(Object[] r) {
        return new ConformityRow((UUID) r[0], (String) r[1], r[2] == null ? null : ((Number) r[2]).longValue(), (String) r[3], (String) r[4],
                Times.toInstant(r[5]), Times.toInstant(r[6]), Times.toInstant(r[7]), (String) r[8], Times.toInstant(r[9]), (BigDecimal) r[10],
                (String) r[11], r[12] == null ? null : Times.toLocalDate(r[12]),
                (String) r[13]);
    }
}
