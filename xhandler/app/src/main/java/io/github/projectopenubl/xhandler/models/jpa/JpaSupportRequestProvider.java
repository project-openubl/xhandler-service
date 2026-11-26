package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.SupportRequestProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaSupportRequestProvider implements SupportRequestProvider {

    private final EntityManager em;

    public JpaSupportRequestProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<UUID> requestSupportSession(UUID tenantId, String operator, String reason) {
        Object id = em.createNativeQuery("SELECT xhandler.request_support_session(:tenant, :operator, :reason)")
                .setParameter("tenant", tenantId)
                .setParameter("operator", operator)
                .setParameter("reason", reason)
                .getSingleResult();
        return Optional.ofNullable((UUID) id);
    }
}
