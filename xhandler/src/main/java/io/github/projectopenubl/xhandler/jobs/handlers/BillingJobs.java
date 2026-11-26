package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.clients.LagoClient;
import io.github.projectopenubl.xhandler.managers.EntitlementManager;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.BillingProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/** Lago synchronization (DESIGN.md §14): usage out of the outbox, plans back into entitlements. */
public final class BillingJobs {

    private static final Logger LOG = Logger.getLogger(BillingJobs.class);

    private BillingJobs() {}

    /** Delivers the usage outbox to Lago; events wait there while Lago is unreachable. */
    @ApplicationScoped
    public static class UsageDelivery implements JobHandler {

        static final int BATCH = 100;

        private final BillingProvider billingProvider;
        private final LagoClient lago;
        private final tools.jackson.databind.ObjectMapper json;

        public UsageDelivery(BillingProvider billingProvider, LagoClient lago, tools.jackson.databind.ObjectMapper json) {
            this.billingProvider = billingProvider;
            this.lago = lago;
            this.json = json;
        }

        @Override
        public String name() {
            return "billing.usage-delivery";
        }

        @Override
        public Map<String, Object> run(JobContext context) throws Exception {
            if (!lago.configured()) {
                return Map.of("skipped", "lago not configured");
            }
            int sent = 0;
            while (true) {
                record Event(UUID id, UUID tenant, String type, int quantity, String properties, Instant at) {}
                List<Event> batch = QuarkusTransaction.requiringNew().call(() -> billingProvider.getUnsentUsageEvents(BATCH).stream()
                        .map(r -> new Event(r.id(), r.tenantId(), r.type(), r.quantity(), r.properties(), r.createdAt())).toList());
                if (batch.isEmpty()) {
                    return Map.of("sent", sent);
                }
                List<Map<String, Object>> events = new ArrayList<>();
                for (Event e : batch) {
                    Map<String, Object> properties = new LinkedHashMap<>(json.readValue(e.properties(),
                            new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}));
                    properties.put("quantity", e.quantity());
                    events.add(Map.of("transaction_id", e.id().toString(), "external_subscription_id", e.tenant().toString(),
                            "code", e.type().replace('.', '_'), "timestamp", e.at().getEpochSecond(), "properties", properties));
                }
                List<UUID> ids;
                try {
                    lago.sendEvents(events);
                    ids = batch.stream().map(Event::id).toList();
                } catch (LagoClient.Rejected rejected) {
                    // One bad event must not block the rest: send them one by one, set the bad ones aside
                    ids = new ArrayList<>();
                    for (int i = 0; i < events.size(); i++) {
                        UUID eventId = batch.get(i).id();
                        try {
                            lago.sendEvents(List.of(events.get(i)));
                            ids.add(eventId);
                        } catch (LagoClient.Rejected bad) {
                            LOG.errorf("Lago refused usage event %s: %s", eventId, bad.getMessage());
                            QuarkusTransaction.requiringNew().run(() -> billingProvider.markUsageFailed(eventId, bad.getMessage()));
                        }
                    }
                }
                List<UUID> sentIds = ids;
                if (!sentIds.isEmpty()) {
                    QuarkusTransaction.requiringNew().run(() -> billingProvider.markUsageSent(sentIds));
                }
                sent += batch.size();
                if (batch.size() < BATCH) {
                    return Map.of("sent", sent);
                }
            }
        }
    }

    /** Refreshes cached entitlements from Lago's subscriptions. */
    @ApplicationScoped
    public static class EntitlementSync implements JobHandler {

        private final BillingProvider billingProvider;
        private final LagoClient lago;
        private final TenantAccess access;
        private final EntitlementManager entitlements;

        public EntitlementSync(BillingProvider billingProvider, LagoClient lago, TenantAccess access, EntitlementManager entitlements) {
            this.billingProvider = billingProvider;
            this.lago = lago;
            this.access = access;
            this.entitlements = entitlements;
        }

        @Override
        public String name() {
            return "billing.entitlement-sync";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            if (!lago.configured()) {
                return Map.of("skipped", "lago not configured");
            }
            List<UUID> tenants = QuarkusTransaction.requiringNew().call(billingProvider::getEntitlementsToSync);
            int synced = 0;
            for (UUID tenant : tenants) {
                try {
                    String plan = lago.activePlan(tenant.toString()).orElse("free");
                    QuarkusTransaction.requiringNew().run(() -> {
                        access.enterAsPlatform(tenant);
                        if (!entitlements.known(plan)) {
                            // Never downgrade a paying customer because of an unmapped Lago plan
                            LOG.errorf("Lago plan %s of tenant %s is not in the plan table: keeping its current plan", plan, tenant);
                            return;
                        }
                        entitlements.setPlan(tenant, plan, "lago");
                    });
                    synced++;
                } catch (Exception e) {
                    // Keep the cached plan; after 24 hours without a sync it falls back to free
                    LOG.warnf("entitlement sync for tenant %s failed: %s", tenant, e.getMessage());
                }
            }
            return Map.of("synced", synced, "candidates", tenants.size());
        }
    }
}
