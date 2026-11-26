package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.UUID;

/** The queue of due webhook deliveries, across tenants; joins the caller's transaction. */
public interface WebhookQueueProvider {

    record LeasedDelivery(UUID id, UUID tenantId, int attempts) {}

    /** {@code xhandler.lease_webhook_deliveries}: up to {@code limit} due deliveries, leased for 2 minutes. */
    List<LeasedDelivery> leaseWebhookDeliveries(int limit);
}
