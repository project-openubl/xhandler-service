package io.github.projectopenubl.xhandler.events;

import io.github.projectopenubl.xhandler.models.jpa.entities.WebhookDeliveryEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.WebhookSubscriptionEntity;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns domain events into webhook deliveries (DESIGN.md §12), in the transaction that caused them
 * (an outbox: nothing is delivered for a rolled-back change). Payloads are CloudEvents 1.0 with a
 * versioned {@code dataschema}.
 */
@ApplicationScoped
public class WebhookEvents {

    public static final String SOURCE = "urn:xhandler-service";
    public static final String DOCUMENT_SCHEMA = "urn:xhandler:schema:document-event:v1";

    /** Status changes become document.* events. */
    void onStatusChanged(@Observes DocumentStatusChanged change) {
        DocumentEntity d = change.document();
        String type = switch (d.getStatus()) {
            case "accepted", "accepted_with_observations" -> "document.accepted";
            case "rejected" -> "document.rejected";
            case "exception" -> "document.exception";
            case "voided" -> "document.voided";
            default -> null;
        };
        if (type == null) {
            return;
        }
        publish(d.getTenantId(), type, "xh:document:" + d.getId(), DOCUMENT_SCHEMA, documentData(d));
        if ("rejected".equals(d.getStatus()) && d.getDeliveredAt() != null) {
            // A document the buyer already has was rejected: warn (§9.3)
            publish(d.getTenantId(), "document.rejected_after_delivery", "xh:document:" + d.getId(), DOCUMENT_SCHEMA, documentData(d));
        }
    }

    /** Queues an event for every active subscription of the tenant that wants it. */
    @Transactional(Transactional.TxType.MANDATORY)
    public int publish(UUID tenantId, String type, String subject, String dataSchema, Map<String, Object> data) {
        List<WebhookSubscriptionEntity> subscriptions = WebhookSubscriptionEntity.list("tenantId = ?1 and active and deletedAt is null", tenantId);
        UUID eventId = Ids.newId();
        Instant now = Instant.now();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("specversion", "1.0");
        event.put("id", eventId.toString());
        event.put("source", SOURCE);
        event.put("type", type);
        event.put("subject", subject);
        event.put("time", now.toString());
        event.put("datacontenttype", "application/json");
        event.put("dataschema", dataSchema);
        event.put("data", data);
        int queued = 0;
        for (WebhookSubscriptionEntity subscription : subscriptions) {
            if (!subscription.wants(type)) {
                continue;
            }
            WebhookDeliveryEntity delivery = new WebhookDeliveryEntity();
            delivery.setId(Ids.newId());
            delivery.setTenantId(tenantId);
            delivery.setSubscriptionId(subscription.getId());
            delivery.setEventId(eventId);
            delivery.setEventType(type);
            delivery.setPayload(event);
            delivery.setNextAttemptAt(now);
            delivery.setCreatedAt(now);
            delivery.setUpdatedAt(now);
            delivery.persist();
            queued++;
        }
        return queued;
    }

    public static Map<String, Object> documentData(DocumentEntity d) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", d.getId().toString());
        data.put("prn", "xh:document:" + d.getId());
        data.put("company_id", d.getCompanyId().toString());
        data.put("sunat_env", d.getSunatEnv());
        data.put("tipo_comprobante", d.getTipoComprobante());
        data.put("serie_numero", d.serieNumero());
        data.put("fecha_emision", d.getFechaEmision().toString());
        data.put("status", d.getStatus());
        data.put("sunat_code", d.getSunatCode());
        data.put("sunat_description", d.getSunatDescription());
        data.put("delivered", d.getDeliveredAt() != null);
        data.put("labels", d.getLabels());
        return data;
    }
}
