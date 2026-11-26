package io.github.projectopenubl.xhandler.models;

import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link WebhookDeliveryModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface WebhookDeliveryProvider {

    /** Creates a webhookDelivery with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted webhookDelivery with {@code id}; see {@link #persist}. */
    WebhookDeliveryModel newWebhookDelivery(UUID id);

    void persist(WebhookDeliveryModel webhookDelivery);

    void delete(WebhookDeliveryModel webhookDelivery);

    WebhookDeliveryModel addWebhookDelivery(UUID id, Consumer<WebhookDeliveryModel> init);

    Optional<WebhookDeliveryModel> getWebhookDelivery(UUID id);

    /** Same as {@link #getWebhookDelivery}, locking the row until the transaction ends. */
    Optional<WebhookDeliveryModel> getWebhookDeliveryForUpdate(UUID id);

    /** Same as {@link #getWebhookDelivery}, share-locking the row until the transaction ends. */
    Optional<WebhookDeliveryModel> getWebhookDeliveryForShare(UUID id);

    /** Re-reads the webhookDelivery from the database (after native updates that bypass the session). */
    void refresh(WebhookDeliveryModel webhookDelivery);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    /** Deliveries of a subscription, newest first; {@code status} is an optional filter. */
    List<WebhookDeliveryModel> getWebhookDeliveries(UUID subscriptionId, String status, int offset, int limit);
}
