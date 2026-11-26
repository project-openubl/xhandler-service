package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.managers.Notifications;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.NotificationProvider;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scheduled tenant notices (DESIGN.md §26.6): expiring certificates and SUNAT outages. */
public final class PlatformNotices {

    private PlatformNotices() {}

    /** Certificates expiring in 30, 7 or 1 days, each threshold notified once. */
    @ApplicationScoped
    public static class CertificateExpiry implements JobHandler {

        private final CompanyProvider companyProvider;

        private final NotificationProvider notificationProvider;
        private final TenantAccess access;
        private final Notifications notifications;

        public CertificateExpiry(NotificationProvider notificationProvider, TenantAccess access, Notifications notifications, CompanyProvider companyProvider) {
            this.companyProvider = companyProvider;
            this.notificationProvider = notificationProvider;
            this.access = access;
            this.notifications = notifications;
        }

        @Override
        public String name() {
            return "platform.certificate-expiry";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            int notified = 0;
            for (int days : new int[] {1, 7, 30}) {
                record Expiring(UUID tenantId, UUID companyId, UUID keyId, Instant validUntil) {}
                List<Expiring> expiring = QuarkusTransaction.requiringNew().call(() -> notificationProvider.getExpiringCertificates(days).stream()
                        .map(r -> new Expiring(r.tenantId(), r.companyId(), r.signingKeyId(), r.validUntil())).toList());
                for (Expiring e : expiring) {
                    QuarkusTransaction.requiringNew().run(() -> {
                        access.enterAsPlatform(e.tenantId());
                        var company = companyProvider.getCompany(e.companyId()).orElse(null);
                        long left = Math.max(0, ChronoUnit.DAYS.between(Instant.now(), e.validUntil()));
                        notifications.publish(e.tenantId(), Notifications.CERTIFICATE_EXPIRING,
                                "Certificado digital por vencer: " + company.getRuc(),
                                "El certificado digital de " + company.getRazonSocial() + " vence en " + left + " día(s) (" + e.validUntil()
                                        + "). Suba uno nuevo para seguir firmando.", null);
                        notificationProvider.markCertificateExpiryNotified(e.keyId(), days);
                    });
                    notified++;
                }
            }
            // The platform's own keys (PSE, OSE CDR): their expiry stops every company using them
            QuarkusTransaction.requiringNew().run(() -> {
                for (NotificationProvider.ExpiringPlatformKey key : notificationProvider.getExpiringPlatformKeys()) {
                    org.jboss.logging.Logger.getLogger("xhandler.audit.platform").errorf("platform %s certificate expires at %s: replace it", key.purpose(),
                            key.validUntil());
                }
            });
            return Map.of("notified", notified);
        }
    }

    /** SUNAT outage started or ended on an endpoint: tenants with documents waiting there are told. */
    @ApplicationScoped
    public static class SunatHealth implements JobHandler {

        private final NotificationProvider notificationProvider;
        private final TenantAccess access;
        private final Notifications notifications;

        public SunatHealth(NotificationProvider notificationProvider, TenantAccess access, Notifications notifications) {
            this.notificationProvider = notificationProvider;
            this.access = access;
            this.notifications = notifications;
        }

        @Override
        public String name() {
            return "platform.sunat-health";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            record Change(String endpoint, String state) {}
            // Debounced: an endpoint is "down" after 5 minutes with its breaker not closed, and "up"
            // is only announced after a "down" notice. Breakers flap during partial outages.
            List<Change> changes = QuarkusTransaction.requiringNew().call(() -> notificationProvider.detectEndpointChanges().stream()
                    .map(r -> new Change(r.endpoint(), r.state())).toList());
            for (Change change : changes) {
                // Record the state first: a failing tenant must not make everyone be notified again
                QuarkusTransaction.requiringNew().run(() -> notificationProvider.setEndpointAlertState(change.endpoint(), change.state()));
                List<UUID> tenants = QuarkusTransaction.requiringNew().call(() -> notificationProvider.getTenantsWaitingOn(change.endpoint()));
                for (UUID tenant : tenants) {
                    try {
                        QuarkusTransaction.requiringNew().run(() -> {
                            access.enterAsPlatform(tenant);
                            boolean down = "down".equals(change.state());
                            notifications.publish(tenant, Notifications.SUNAT_OUTAGE, down ? "SUNAT no responde" : "SUNAT volvió a responder",
                                    down ? "SUNAT no está respondiendo. Sus documentos están guardados y se enviarán automáticamente cuando vuelva."
                                            : "SUNAT volvió a responder. Los documentos pendientes se están enviando por orden de plazo.", null);
                        });
                    } catch (RuntimeException e) {
                        org.jboss.logging.Logger.getLogger(SunatHealth.class).warnf("outage notice for tenant %s failed: %s", tenant, e.getMessage());
                    }
                }
            }
            return Map.of("changes", changes.size());
        }
    }
}
