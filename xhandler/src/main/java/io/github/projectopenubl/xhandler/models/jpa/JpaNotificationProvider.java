package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.NotificationProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaNotificationProvider implements NotificationProvider {

    private final EntityManager em;

    public JpaNotificationProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<NotificationPreference> getNotificationPreference(String kind) {
        List<?> preference = em.createNativeQuery("SELECT email, in_app FROM xhandler.notification_preference WHERE kind = :k")
                .setParameter("k", kind).getResultList();
        return preference.stream().findFirst().map(p -> {
            Object[] r = (Object[]) p;
            return new NotificationPreference(kind, (Boolean) r[0], (Boolean) r[1]);
        });
    }

    @Override
    public void addNotification(UUID id, UUID tenantId, String kind, String title, String body, UUID documentId) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.notification (id, tenant_id, kind, title, body, document_id)
                        VALUES (:id, :tenant, :kind, :title, :body, :document)""")
                .setParameter("id", id).setParameter("tenant", tenantId).setParameter("kind", kind)
                .setParameter("title", title).setParameter("body", body).setParameter("document", documentId)
                .execute();
    }

    @Override
    public List<NotificationRow> getNotifications(boolean unreadOnly, int limit, int offset) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, kind, title, body, document_id, created_at, read_at FROM xhandler.notification"
                        + (unreadOnly ? " WHERE read_at IS NULL" : "") + " ORDER BY created_at DESC LIMIT :l OFFSET :o")
                .setParameter("l", limit).setParameter("o", offset).getResultList();
        return rows.stream().map(r -> new NotificationRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (UUID) r[4],
                Times.toInstant(r[5]), Times.toInstant(r[6]))).toList();
    }

    @Override
    public boolean markNotificationRead(UUID id) {
        int updated = em.createNativeStatement("UPDATE xhandler.notification SET read_at = coalesce(read_at, now()) WHERE id = :id")
                .setParameter("id", id).execute();
        return updated != 0;
    }

    @Override
    public void markAllNotificationsRead() {
        em.createNativeStatement("UPDATE xhandler.notification SET read_at = now() WHERE read_at IS NULL").execute();
    }

    @Override
    public List<NotificationPreference> getNotificationPreferences() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT kind, email, in_app FROM xhandler.notification_preference ORDER BY kind")
                .getResultList();
        return rows.stream().map(r -> new NotificationPreference((String) r[0], (Boolean) r[1], (Boolean) r[2])).toList();
    }

    @Override
    public void setNotificationPreference(UUID tenantId, String kind, boolean email, boolean inApp) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.notification_preference (tenant_id, kind, email, in_app) VALUES (:t, :k, :e, :i)
                        ON CONFLICT (tenant_id, kind) DO UPDATE SET email = excluded.email, in_app = excluded.in_app""")
                .setParameter("t", tenantId).setParameter("k", kind).setParameter("e", email).setParameter("i", inApp)
                .execute();
    }

    @Override
    public List<ExpiringCertificate> getExpiringCertificates(int days) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT tenant_id, company_id, signing_key_id, valid_until FROM xhandler.expiring_certificates(:d)")
                .setParameter("d", days).getResultList();
        return rows.stream().map(r -> new ExpiringCertificate((UUID) r[0], (UUID) r[1], (UUID) r[2], Times.toInstant(r[3]))).toList();
    }

    @Override
    public void markCertificateExpiryNotified(UUID signingKeyId, int days) {
        em.createNativeStatement("UPDATE xhandler.signing_key SET expiry_notified_days = :d WHERE id = :id")
                .setParameter("d", days).setParameter("id", signingKeyId).execute();
    }

    @Override
    public List<ExpiringPlatformKey> getExpiringPlatformKeys() {
        @SuppressWarnings("unchecked")
        List<Object[]> platform = em.createNativeQuery("""
                        SELECT purpose, valid_until FROM xhandler.platform_key WHERE status = 'active' AND valid_until < now() + interval '30 days'""")
                .getResultList();
        return platform.stream().map(r -> new ExpiringPlatformKey((String) r[0], Times.toInstant(r[1]))).toList();
    }

    @Override
    public List<EndpointChange> detectEndpointChanges() {
        em.createNativeStatement("""
                        INSERT INTO xhandler.endpoint_alert (endpoint, state)
                        SELECT e.id, 'suspect' FROM xhandler.sunat_endpoint e
                        WHERE e.id LIKE 'production:%' AND e.breaker_state <> 'closed'
                        ON CONFLICT (endpoint) DO UPDATE SET state = 'suspect', at = now()
                        WHERE endpoint_alert.state = 'up'""").execute();
        em.createNativeStatement("""
                        UPDATE xhandler.endpoint_alert a SET state = 'up', at = now() FROM xhandler.sunat_endpoint e
                        WHERE e.id = a.endpoint AND a.state = 'suspect' AND e.breaker_state = 'closed'""").execute();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT a.endpoint, CASE WHEN a.state = 'suspect' THEN 'down' ELSE 'up' END
                        FROM xhandler.endpoint_alert a JOIN xhandler.sunat_endpoint e ON e.id = a.endpoint
                        WHERE (a.state = 'suspect' AND e.breaker_state <> 'closed' AND a.at < now() - interval '5 minutes')
                           OR (a.state = 'down' AND e.breaker_state = 'closed')""")
                .getResultList();
        return rows.stream().map(r -> new EndpointChange((String) r[0], (String) r[1])).toList();
    }

    @Override
    public void setEndpointAlertState(String endpoint, String state) {
        em.createNativeStatement("UPDATE xhandler.endpoint_alert SET state = :s, at = now() WHERE endpoint = :e")
                .setParameter("e", endpoint).setParameter("s", state).execute();
    }

    @Override
    public List<UUID> getTenantsWaitingOn(String endpoint) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, documents FROM xhandler.tenants_waiting_on(:e)")
                .setParameter("e", endpoint).getResultList();
        return rows.stream().map(r -> (UUID) r[0]).toList();
    }
}
