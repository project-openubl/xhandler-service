package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link WebhookSubscriptionModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface WebhookSubscriptionProvider {

    /** Creates a webhookSubscription with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted webhookSubscription with {@code id}; see {@link #persist}. */
    WebhookSubscriptionModel newWebhookSubscription(UUID id);

    void persist(WebhookSubscriptionModel webhookSubscription);

    void delete(WebhookSubscriptionModel webhookSubscription);

    WebhookSubscriptionModel addWebhookSubscription(UUID id, Consumer<WebhookSubscriptionModel> init);

    Optional<WebhookSubscriptionModel> getWebhookSubscription(UUID id);

    /** Same as {@link #getWebhookSubscription}, locking the row until the transaction ends. */
    Optional<WebhookSubscriptionModel> getWebhookSubscriptionForUpdate(UUID id);

    /** Same as {@link #getWebhookSubscription}, share-locking the row until the transaction ends. */
    Optional<WebhookSubscriptionModel> getWebhookSubscriptionForShare(UUID id);

    /** Re-reads the webhookSubscription from the database (after native updates that bypass the session). */
    void refresh(WebhookSubscriptionModel webhookSubscription);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    List<WebhookSubscriptionModel> getActiveWebhookSubscriptions(UUID tenantId);

    List<WebhookSubscriptionModel> getWebhookSubscriptions();
}
