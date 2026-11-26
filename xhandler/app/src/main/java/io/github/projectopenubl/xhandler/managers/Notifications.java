package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.jobs.handlers.NotificationEmailJob;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.jobs.handlers.DeadlineAlerts;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.github.projectopenubl.xhandler.models.NotificationProvider;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant notifications (DESIGN.md §26.6), separate from webhooks: in-app, and emailed to the
 * tenant's notification address, per the tenant's preferences per kind. Written in the transaction
 * of the event; emails go out through a job.
 */
@ApplicationScoped
public class Notifications {

    private final TenantProvider tenantProvider;

    public static final String DOCUMENT_EXCEPTION = "document.exception";
    public static final String DOCUMENT_AT_RISK = "document.at_risk";
    public static final String REJECTED_AFTER_DELIVERY = "document.rejected_after_delivery";
    public static final String CERTIFICATE_EXPIRING = "certificate.expiring";
    public static final String SUNAT_OUTAGE = "sunat.outage";
    public static final String QUOTA = "quota";
    public static final String PSE_AUTHORIZATION = "pse.authorization_revoked";
    public static final String CONFORMITY_OBJECTED = "conformity.objected";
    public static final String SIRE_DEADLINE = "sire.deadline";

    private final NotificationProvider notificationProvider;
    private final Jobs jobs;

    public Notifications(NotificationProvider notificationProvider, Jobs jobs, TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.notificationProvider = notificationProvider;
        this.jobs = jobs;
    }

    /** With the tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void publish(UUID tenantId, String kind, String title, String body, UUID documentId) {
        java.util.Optional<NotificationProvider.NotificationPreference> preference = notificationProvider.getNotificationPreference(kind);
        // Legal notices (data-subject requests) can't be muted
        boolean mandatory = kind.startsWith("privacy.");
        boolean email = mandatory || preference.isEmpty() || Boolean.TRUE.equals(preference.get().email());
        boolean inApp = mandatory || preference.isEmpty() || Boolean.TRUE.equals(preference.get().inApp());
        if (inApp) {
            notificationProvider.addNotification(Ids.newId(), tenantId, kind, title, body, documentId);
        }
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        if (email && tenant != null && tenant.getNotificationEmail() != null) {
            jobs.enqueue(new TenantScope(tenantId, Actor.PLATFORM, Role.PLATFORM), NotificationEmailJob.NAME, 3,
                    Map.of("to", tenant.getNotificationEmail(), "title", title, "body", body), List.of(), null);
        }
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        DocumentModel d = change.document();
        if (d.isSummary()) {
            return;
        }
        if ("exception".equals(d.getStatus())) {
            publish(d.getTenantId(), DOCUMENT_EXCEPTION, "Documento con error: " + d.serieNumero(),
                    "SUNAT no aceptó procesar el documento " + d.serieNumero() + ": " + nz(d.getSunatDescription())
                            + ". Corrija la causa y reenvíelo.", d.getId());
        } else if ("rejected".equals(d.getStatus()) && d.getDeliveredAt() != null) {
            publish(d.getTenantId(), REJECTED_AFTER_DELIVERY, "Documento rechazado ya entregado: " + d.serieNumero(),
                    "SUNAT rechazó el documento " + d.serieNumero() + ", que ya fue entregado al comprador: " + nz(d.getSunatDescription())
                            + ". Avise al comprador.", d.getId());
        }
    }

    void onAtRisk(@Observes DeadlineAlerts.DocumentAtRisk risk) {
        DocumentModel d = risk.document();
        publish(d.getTenantId(), DOCUMENT_AT_RISK, "Documento cerca de su plazo: " + d.serieNumero(),
                "El documento " + d.serieNumero() + " aún no tiene respuesta de SUNAT y su plazo legal vence el " + d.getDeadlineAt()
                        + ". Se sigue reintentando.", d.getId());
    }

    private static String nz(String value) {
        return value == null ? "sin detalle" : value;
    }
}
