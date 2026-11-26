package io.github.projectopenubl.xhandler.events;

import io.github.projectopenubl.xhandler.models.DocumentModel;

import io.github.projectopenubl.xhandler.utils.Ids;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.github.projectopenubl.xhandler.models.BillingProvider;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;

/**
 * Usage for billing (DESIGN.md §14): written to {@code usage_event_outbox} in the business
 * transaction, delivered to Lago by a job. Documents count when SUNAT accepts them.
 */
@ApplicationScoped
public class UsageEvents {

    private final BillingProvider billingProvider;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public UsageEvents(BillingProvider billingProvider, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.billingProvider = billingProvider;
        this.json = json;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        DocumentModel d = change.document();
        if (DocumentModel.ACCEPTED.contains(d.getStatus()) && !DocumentModel.ACCEPTED.contains(change.previousStatus()) && !d.isSummary()) {
            record(d.getTenantId(), "document.accepted", Map.of("tipo_comprobante", d.getTipoComprobante(), "company_id", d.getCompanyId().toString(),
                    "sunat_env", d.getSunatEnv()));
        }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(UUID tenantId, String type, Map<String, Object> properties) {
        try {
            billingProvider.addUsageEvent(Ids.newId(), tenantId, type, json.writeValueAsString(properties));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
