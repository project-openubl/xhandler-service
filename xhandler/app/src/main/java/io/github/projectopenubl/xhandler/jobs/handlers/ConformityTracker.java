package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.events.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineException;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.managers.Notifications;
import io.github.projectopenubl.xhandler.managers.CredentialService;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.events.WebhookEvents;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.github.projectopenubl.xhandler.models.ConformityProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Factura negociable conformity (DESIGN.md §11.8): a credit factura accepted by SUNAT opens an
 * 8-day window for the buyer (DU 013-2020). This job polls SUNAT's conformity API with the
 * company's SIRE API credentials, records agreed, objected or silent agreement, and tells the
 * tenant (webhooks {@code document.conformity_*}, notifications), with a reminder 2 days before
 * the window closes. SUNAT is called with no transaction open (§10.8).
 */
@ApplicationScoped
public class ConformityTracker implements JobHandler {

    private final DocumentProvider documentProvider;

    private final CompanyProvider companyProvider;

    static final Duration WINDOW = Duration.ofDays(8);
    static final Duration REMIND_BEFORE = Duration.ofDays(2);
    private static final Logger LOG = Logger.getLogger(ConformityTracker.class);

    private final ConformityProvider conformityProvider;
    private final TenantAccess access;
    private final Engine engine;
    private final CredentialService credentials;
    private final WebhookEvents webhooks;
    private final Notifications notifications;

    public ConformityTracker(ConformityProvider conformityProvider, TenantAccess access, Engine engine, CredentialService credentials, WebhookEvents webhooks,
            Notifications notifications, CompanyProvider companyProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.companyProvider = companyProvider;
        this.conformityProvider = conformityProvider;
        this.access = access;
        this.engine = engine;
        this.credentials = credentials;
        this.webhooks = webhooks;
        this.notifications = notifications;
    }

    /** Inside the transaction that recorded SUNAT's acceptance: no I/O, one insert. SIRE has no beta. */
    void onAccepted(@Observes DocumentStatusChanged change) {
        DocumentModel d = change.document();
        if ("voided".equals(d.getStatus()) && "01".equals(d.getTipoComprobante())) {
            // A voided factura is no longer negotiable: stop following it
            conformityProvider.closeConformity(d.getId());
            return;
        }
        // Facturas negociables are between companies: the buyer is identified by RUC
        if (!"01".equals(d.getTipoComprobante()) || !"credito".equals(d.getPaymentTerms()) || !DocumentModel.ACCEPTED.contains(d.getStatus())
                || DocumentModel.ACCEPTED.contains(change.previousStatus()) || "beta".equals(d.getSunatEnv()) || !"6".equals(d.getCustomerTipo())) {
            return;
        }
        Instant now = Instant.now();
        conformityProvider.addConformity(Ids.newId(), d.getTenantId(), d.getCompanyId(), d.getId(), d.getCustomerNumero(), now, now.plus(WINDOW));
    }

    @Override
    public String name() {
        return "conformity.poll";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        List<ConformityProvider.DueConformity> due = QuarkusTransaction.requiringNew().call(conformityProvider::getConformitiesToCheck);
        int resolved = 0;
        for (ConformityProvider.DueConformity d : due) {
            try {
                if (check(d.tenantId(), d.conformityId())) {
                    resolved++;
                }
            } catch (RuntimeException e) {
                LOG.warnf("conformity check %s failed: %s", d.conformityId(), e.toString());
                stamp(d.tenantId(), d.conformityId(), "Error al consultar: se reintentará.");
            }
        }
        return Map.of("checked", due.size(), "resolved", resolved);
    }

    /** True when SUNAT gave a final answer. */
    public boolean check(UUID tenantId, UUID conformityId) {
        record Call(UUID documentId, Instant windowEnds, Instant remindedAt, String sunatEnv, EngineModels.ConformityRequest request) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            ConformityProvider.ConformityCheck c = conformityProvider.getConformityCheck(conformityId);
            DocumentModel doc = documentProvider.getDocument(c.documentId()).orElse(null);
            CompanyModel company = companyProvider.getCompany(doc.getCompanyId()).orElse(null);
            EngineModels.ConformityRequest request = credentials.read(scope, company, "sire", "conformity:" + doc.getId())
                    .map(f -> new EngineModels.ConformityRequest("fake".equals(doc.getSunatEnv()) ? EngineModels.Environment.FAKE
                            : EngineModels.Environment.PRODUCTION,
                            new EngineModels.ApiCredentials(f.get("client_id"), f.get("client_secret"), f.get("username"), f.get("password")),
                            company.getRuc(), doc.getTipoComprobante(), doc.getSerie(), doc.getNumero()))
                    .orElse(null);
            return new Call(doc.getId(), c.windowEndsAt(), c.remindedAt(), doc.getSunatEnv(), request);
        });
        if (call.request() == null) {
            remindIfClosing(tenantId, conformityId, call.documentId(), call.windowEnds(), call.remindedAt());
            stamp(tenantId, conformityId, "Registre las credenciales de la API SIRE para consultar la conformidad.");
            return false;
        }
        EngineModels.ConformityResponse response;
        try {
            response = engine.conformity(call.request());
        } catch (EngineException e) {
            stamp(tenantId, conformityId, "El servicio no respondió; se reintentará.");
            remindIfClosing(tenantId, conformityId, call.documentId(), call.windowEnds(), call.remindedAt());
            return false;
        }
        if (!"ok".equals(response.state())) {
            stamp(tenantId, conformityId, "unauthorized".equals(response.state()) ? "SUNAT rechazó las credenciales de la API SIRE."
                    : "SUNAT no respondió; se reintentará.");
            remindIfClosing(tenantId, conformityId, call.documentId(), call.windowEnds(), call.remindedAt());
            return false;
        }
        String state = switch (response.conformity()) {
            case "agreed" -> "agreed";
            case "objected" -> "objected";
            case "silent_agreement" -> "silent_agreement";
            default -> null;
        };
        if (state == null) {
            // Still pending at SUNAT (checked first, so no reminder goes out after the buyer answered)
            stamp(tenantId, conformityId, "pending".equals(response.conformity()) ? "" : "SUNAT aún no registra el comprobante.");
            remindIfClosing(tenantId, conformityId, call.documentId(), call.windowEnds(), call.remindedAt());
            return false;
        }
        resolve(tenantId, conformityId, call.documentId(), state);
        return true;
    }

    private void resolve(UUID tenantId, UUID conformityId, UUID documentId, String state) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            int changed = conformityProvider.resolveConformity(conformityId, state);
            if (changed == 0) {
                return;
            }
            DocumentModel doc = documentProvider.getDocument(documentId).orElse(null);
            Map<String, Object> data = new LinkedHashMap<>(WebhookEvents.documentData(doc));
            data.put("conformity", state);
            webhooks.publish(tenantId, "document.conformity_" + state, "xh:document:" + documentId, WebhookEvents.DOCUMENT_SCHEMA, data);
            String text = switch (state) {
                case "agreed" -> "El adquirente dio su conformidad a la factura " + doc.serieNumero() + ".";
                case "objected" -> "El adquirente registró su disconformidad con la factura " + doc.serieNumero()
                        + ". Emita una nota de crédito tipo 13 para corregir el monto o las fechas de pago.";
                default -> "La factura " + doc.serieNumero() + " obtuvo la conformidad presunta (pasaron 8 días sin respuesta del adquirente).";
            };
            notifications.publish(tenantId, "conformity." + state, "Factura negociable " + doc.serieNumero(), text, documentId);
        });
    }

    private void remindIfClosing(UUID tenantId, UUID conformityId, UUID documentId, Instant windowEnds, Instant remindedAt) {
        Instant now = Instant.now();
        if (remindedAt != null || windowEnds == null || now.isBefore(windowEnds.minus(REMIND_BEFORE)) || now.isAfter(windowEnds)) {
            return;
        }
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            int changed = conformityProvider.markReminded(conformityId);
            if (changed == 0) {
                return;
            }
            DocumentModel doc = documentProvider.getDocument(documentId).orElse(null);
            Map<String, Object> data = new LinkedHashMap<>(WebhookEvents.documentData(doc));
            data.put("window_ends_at", windowEnds.toString());
            webhooks.publish(tenantId, "document.conformity_reminder", "xh:document:" + documentId, WebhookEvents.DOCUMENT_SCHEMA, data);
            notifications.publish(tenantId, "conformity.reminder", "Factura negociable " + doc.serieNumero(),
                    "El plazo de conformidad de la factura " + doc.serieNumero() + " vence el " + windowEnds + ".", documentId);
        });
    }

    private void stamp(UUID tenantId, UUID conformityId, String detail) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            conformityProvider.stampConformity(conformityId, detail);
        });
    }
}
