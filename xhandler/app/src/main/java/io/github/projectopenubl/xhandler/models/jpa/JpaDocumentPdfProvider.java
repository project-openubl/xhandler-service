package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.DocumentPdfProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaDocumentPdfProvider implements DocumentPdfProvider {

    private final EntityManager em;

    public JpaDocumentPdfProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<String> getPartnerBranding(UUID partnerId) {
        Object raw = em.createNativeQuery("SELECT CAST(branding AS text) FROM xhandler.partner WHERE id = :id")
                .setParameter("id", partnerId)
                .getResultStream().findFirst().orElse(null);
        return Optional.ofNullable(raw).map(Object::toString);
    }

    @Override
    public Optional<String> getPartnerLogoRef(UUID partnerId) {
        List<?> partnerLogo = em.createNativeQuery("SELECT logo_ref FROM xhandler.partner WHERE id = :id")
                .setParameter("id", partnerId)
                .getResultList();
        return partnerLogo.isEmpty() ? Optional.empty() : Optional.ofNullable((String) partnerLogo.get(0));
    }
}
