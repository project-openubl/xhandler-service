package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PublicLookupProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPublicLookupProvider implements PublicLookupProvider {

    private final EntityManager em;

    public JpaPublicLookupProvider(EntityManager em) {
        this.em = em;
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
}
