package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.WebhookQueueProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaWebhookQueueProvider implements WebhookQueueProvider {

    private final EntityManager em;

    public JpaWebhookQueueProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<LeasedDelivery> leaseWebhookDeliveries(int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, tenant_id, attempts FROM xhandler.lease_webhook_deliveries(:n, interval '2 minutes')")
                .setParameter("n", limit)
                .getResultList();
        return rows.stream().map(r -> new LeasedDelivery((UUID) r[0], (UUID) r[1], ((Number) r[2]).intValue())).toList();
    }
}
