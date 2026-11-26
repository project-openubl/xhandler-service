package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PublicLookupProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPublicLookupProvider implements PublicLookupProvider {

    private final EntityManager em;

    public JpaPublicLookupProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public int countLookup(String key) {
        return ((Number) em.createNativeQuery("""
                        INSERT INTO xhandler.public_lookup_counter (bucket, key, hits) VALUES (date_trunc('hour', now()), :k, 1)
                        ON CONFLICT (bucket, key) DO UPDATE SET hits = public_lookup_counter.hits + 1 RETURNING hits""")
                .setParameter("k", key).getSingleResult()).intValue();
    }

    @Override
    public String getPublicDocumentStatus(String ruc, String tipoComprobante, String serie, long numero, LocalDate fechaEmision,
            BigDecimal total) {
        Object status = em.createNativeQuery("SELECT xhandler.public_document_status(:ruc, :tipo, :serie, :numero, :fecha, :total)")
                .setParameter("ruc", ruc).setParameter("tipo", tipoComprobante).setParameter("serie", serie)
                .setParameter("numero", numero).setParameter("fecha", fechaEmision).setParameter("total", total)
                .getSingleResult();
        return status == null ? null : status.toString();
    }

    @Override
    public Optional<OseSubmission> getOseSubmission(String issuerRuc, String tipoComprobante, String serie, long numero) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT result, cdr_issued_at, received_at FROM xhandler.ose_submission
                        WHERE issuer_ruc = :r AND tipo = :t AND serie = :s AND numero = :n
                        ORDER BY (result = 'cdr') DESC, received_at DESC LIMIT 1""")
                .setParameter("r", issuerRuc).setParameter("t", tipoComprobante).setParameter("s", serie)
                .setParameter("n", numero).getResultList();
        return rows.stream().findFirst()
                .map(r -> new OseSubmission((String) r[0], Times.toInstant(r[1]), Times.toInstant(r[2])));
    }
}
