package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant notifications, their preferences and the scheduled notices' bookkeeping (DESIGN.md §26.6);
 * every method joins the caller's transaction.
 */
public interface NotificationProvider {

    record NotificationRow(UUID id, String kind, String title, String body, UUID documentId, Instant createdAt, Instant readAt) {}

    record NotificationPreference(String kind, Boolean email, Boolean inApp) {}

    /** A tenant certificate reaching an expiry-notice threshold ({@code xhandler.expiring_certificates}). */
    record ExpiringCertificate(UUID tenantId, UUID companyId, UUID signingKeyId, Instant validUntil) {}

    /** An active platform key expiring within 30 days. */
    record ExpiringPlatformKey(String purpose, Instant validUntil) {}

    /** A SUNAT endpoint whose outage notice is due: {@code state} is {@code down} or {@code up}. */
    record EndpointChange(String endpoint, String state) {}

    /** The open tenant's preference for {@code kind}, if it set one. */
    Optional<NotificationPreference> getNotificationPreference(String kind);

    void addNotification(UUID id, UUID tenantId, String kind, String title, String body, UUID documentId);

    /** Newest first; only unread ones when {@code unreadOnly}. */
    List<NotificationRow> getNotifications(boolean unreadOnly, int limit, int offset);

    /** False when the notification doesn't exist. */
    boolean markNotificationRead(UUID id);

    void markAllNotificationsRead();

    /** Ordered by kind. */
    List<NotificationPreference> getNotificationPreferences();

    void setNotificationPreference(UUID tenantId, String kind, boolean email, boolean inApp);

    /** {@code xhandler.expiring_certificates(days)}. */
    List<ExpiringCertificate> getExpiringCertificates(int days);

    void markCertificateExpiryNotified(UUID signingKeyId, int days);

    List<ExpiringPlatformKey> getExpiringPlatformKeys();

    /**
     * Advances the debounced endpoint alerts (endpoints with a non-closed breaker become suspect,
     * suspect ones whose breaker closed go back up) and returns the endpoints whose notice is due.
     */
    List<EndpointChange> detectEndpointChanges();

    void setEndpointAlertState(String endpoint, String state);

    /** {@code xhandler.tenants_waiting_on(endpoint)}. */
    List<UUID> getTenantsWaitingOn(String endpoint);
}
