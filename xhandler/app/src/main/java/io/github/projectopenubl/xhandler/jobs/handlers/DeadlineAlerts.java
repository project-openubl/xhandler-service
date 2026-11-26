package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.DocumentEventModel;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.events.WebhookEvents;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Deadline alerts (DESIGN.md §9.2.4, §24.1): a document without a definitive answer whose legal
 * deadline (America/Lima) is near gets one {@code document.delayed} webhook, a tenant notification
 * and a timeline entry. It is still sent if the deadline passes, flagged as late.
 */
@ApplicationScoped
public class DeadlineAlerts implements JobHandler {

    private final DocumentProvider documentProvider;

    private final DocumentEventProvider documentEventProvider;

    /** Fired with the tenant open, in the alert's transaction (tenant notifications observe it). */
    public record DocumentAtRisk(DocumentModel document) {}

    private final TenantAccess access;
    private final WebhookEvents webhooks;
    private final Event<DocumentAtRisk> atRisk;
    private final MeterRegistry metrics;
    private final Duration horizon;

    public DeadlineAlerts(TenantAccess access, WebhookEvents webhooks, Event<DocumentAtRisk> atRisk,
            MeterRegistry metrics, @ConfigProperty(name = "xhandler.documents.deadline-alert", defaultValue = "12h") Duration horizon, DocumentEventProvider documentEventProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.documentEventProvider = documentEventProvider;
        this.access = access;
        this.webhooks = webhooks;
        this.atRisk = atRisk;
        this.metrics = metrics;
        this.horizon = horizon;
    }

    @Override
    public String name() {
        return "documents.deadline-alerts";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record AtRisk(UUID tenantId, UUID documentId) {}
        List<AtRisk> documents = QuarkusTransaction.requiringNew().call(() -> {
            return documentProvider.getDocumentsAtRisk(horizon).stream().map(r -> new AtRisk(r.tenantId(), r.id())).toList();
        });
        int alerted = 0;
        for (AtRisk risk : documents) {
            boolean sent = QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(risk.tenantId());
                DocumentModel doc = documentProvider.getDocumentForUpdate(risk.documentId()).orElse(null);
                if (doc == null || doc.getDeadlineAlertedAt() != null || (doc.definitive() && !"exception".equals(doc.getStatus()))) {
                    return false;
                }
                doc.setDeadlineAlertedAt(Instant.now());
                boolean late = doc.getDeadlineAt().isBefore(doc.getDeadlineAlertedAt());
                documentEventProvider.addDocumentEvent(doc, null, late ? "legal deadline passed without SUNAT's answer" : "legal deadline at risk",
                        "platform:worker", context.correlationId());
                Map<String, Object> data = new java.util.LinkedHashMap<>(WebhookEvents.documentData(doc));
                data.put("deadline_at", doc.getDeadlineAt().toString());
                data.put("late", late);
                webhooks.publish(doc.getTenantId(), "document.delayed", "xh:document:" + doc.getId(), WebhookEvents.DOCUMENT_SCHEMA, data);
                atRisk.fire(new DocumentAtRisk(doc));
                return true;
            });
            if (sent) {
                alerted++;
            }
        }
        metrics.counter("xhandler.documents.deadline_alerts").increment(alerted);
        return Map.of("alerted", alerted);
    }
}
