package io.github.projectopenubl.xhandler.events;

import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;

import io.github.projectopenubl.xhandler.utils.Ids;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;

/**
 * Usage for billing (DESIGN.md §14): written to {@code usage_event_outbox} in the business
 * transaction, delivered to Lago by a job. Documents count when SUNAT accepts them.
 */
@ApplicationScoped
public class UsageEvents {

    private final EntityManager em;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public UsageEvents(EntityManager em, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.em = em;
        this.json = json;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        DocumentEntity d = change.document();
        if (DocumentEntity.ACCEPTED.contains(d.getStatus()) && !DocumentEntity.ACCEPTED.contains(change.previousStatus()) && !d.isSummary()) {
            record(d.getTenantId(), "document.accepted", Map.of("tipo_comprobante", d.getTipoComprobante(), "company_id", d.getCompanyId().toString(),
                    "sunat_env", d.getSunatEnv()));
        }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(UUID tenantId, String type, Map<String, Object> properties) {
        try {
            em.createNativeQuery("""
                            INSERT INTO xhandler.usage_event_outbox (id, tenant_id, event_type, properties)
                            VALUES (:id, :tenant, :type, CAST(:properties AS jsonb))""")
                    .setParameter("id", Ids.newId())
                    .setParameter("tenant", tenantId)
                    .setParameter("type", type)
                    .setParameter("properties", json.writeValueAsString(properties))
                    .executeUpdate();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
