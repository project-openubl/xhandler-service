package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Plans, entitlements, the usage outbox and Lago invoices (DESIGN.md §14); every method joins the caller's transaction. */
public interface BillingProvider {

    /** A row of {@code xhandler.plan}. Null limits are unlimited. */
    record PlanRow(String code, String name, Integer productionPerMonth, Integer betaPerDay, Integer sandboxPerDay, Boolean customSmtp,
            Boolean webhooks) {}

    /** The cached entitlement of a tenant. */
    record EntitlementRow(String planCode, String source, Instant syncedAt) {}

    /** Documents created through the API: production this month, beta and the sandbox today. */
    record UsageCounts(long productionThisMonth, long betaToday, long sandboxToday) {}

    /** An outbox event not delivered to Lago yet; {@code properties} is its JSON text. */
    record UnsentUsageEvent(UUID id, UUID tenantId, String type, int quantity, String properties, Instant createdAt) {}

    Optional<EntitlementRow> getEntitlement(UUID tenantId);

    /** Documents with {@code origin = 'api'} created since {@code monthStart} / {@code dayStart}, per environment. */
    UsageCounts countApiDocuments(Instant monthStart, Instant dayStart);

    Optional<PlanRow> getPlan(String code);

    /** The {@code free} plan, which always exists. */
    PlanRow getFreePlan();

    boolean isKnownPlan(String code);

    /** Inserts or replaces the tenant's entitlement, synced now. */
    void upsertEntitlement(UUID tenantId, String planCode, String source);

    /** Inserts a {@code free} entitlement row if the tenant has none. */
    void ensureEntitlement(UUID tenantId);

    /**
     * Records that the {@code percent} (80 or 100) quota notice of {@code period} was sent: 1 when it
     * wasn't recorded for that period yet, 0 otherwise.
     */
    int markQuotaNotified(UUID tenantId, int percent, String period);

    void addUsageEvent(UUID id, UUID tenantId, String type, String propertiesJson);

    /** {@code xhandler.unsent_usage_events(limit)}. */
    List<UnsentUsageEvent> getUnsentUsageEvents(int limit);

    /** {@code xhandler.mark_usage_failed}. */
    void markUsageFailed(UUID id, String error);

    /** {@code xhandler.mark_usage_sent}. */
    void markUsageSent(List<UUID> ids);

    /** {@code xhandler.entitlements_to_sync}: tenants whose plan is due a refresh from Lago. */
    List<UUID> getEntitlementsToSync();

    /** Records a Lago invoice; false when it was already recorded. */
    boolean addLagoInvoice(String lagoInvoiceId, UUID tenantId);

    void setLagoInvoiceDocument(String lagoInvoiceId, UUID documentId);

    /** The {@code platform_setting} values of those of the keys {@code a} and {@code b} that exist. */
    Map<String, String> getPlatformSettings(String a, String b);
}
