package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.representations.idm.EmitRequestRepresentation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.managers.EmitManager;
import io.github.projectopenubl.xhandler.managers.SelfInvoicingManager;
import io.github.projectopenubl.xhandler.models.BillingProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Issues the comprobante for one Lago invoice. */
@ApplicationScoped
public class SelfInvoicingJob implements JobHandler {

    private final DocumentProvider documentProvider;

    private final TenantProvider tenantProvider;

    public static final String NAME = "billing.self-invoice";

    private final BillingProvider billingProvider;
    private final TenantAccess access;
    private final EmitManager emit;
    private final Jobs jobs;
    private final ObjectMapper json;

    public SelfInvoicingJob(BillingProvider billingProvider, TenantAccess access, EmitManager emit, Jobs jobs, ObjectMapper json, TenantProvider tenantProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.tenantProvider = tenantProvider;
        this.billingProvider = billingProvider;
        this.access = access;
        this.emit = emit;
        this.jobs = jobs;
        this.json = json;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> run(JobContext context) throws Exception {
        String lagoId = context.args().path("lago_invoice_id").asString();
        UUID customer = UUID.fromString(context.args().path("tenant_id").asString());
        JsonNode invoice = json.readTree(context.args().path("invoice").asString());
        record Issuer(UUID tenant, UUID company) {}
        Issuer issuer = QuarkusTransaction.requiringNew().call(() -> {
            Map<String, String> settings = billingProvider.getPlatformSettings(SelfInvoicingManager.ISSUER_TENANT, SelfInvoicingManager.ISSUER_COMPANY);
            if (!settings.containsKey(SelfInvoicingManager.ISSUER_TENANT) || !settings.containsKey(SelfInvoicingManager.ISSUER_COMPANY)) {
                throw new IllegalStateException("the platform's issuing company is not configured (admin API billing/issuer)");
            }
            return new Issuer(UUID.fromString(settings.get(SelfInvoicingManager.ISSUER_TENANT)), UUID.fromString(settings.get(SelfInvoicingManager.ISSUER_COMPANY)));
        });
        record Buyer(String ruc, String name, String email) {}
        Buyer buyer = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(customer);
            TenantModel tenant = tenantProvider.getTenant(customer).orElse(null);
            if (tenant == null) {
                throw new IllegalStateException("tenant " + customer + " of Lago invoice " + lagoId + " doesn't exist");
            }
            return new Buyer(tenant.getBillingRuc(), tenant.getBillingName() != null ? tenant.getBillingName() : tenant.getName(), tenant.getBillingEmail());
        });
        // Already issued (a retry after the idempotency window): link it, never issue twice
        Optional<UUID> existing = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(issuer.tenant());
            return documentProvider.getDocumentByLabel(issuer.company(), "lago_invoice", lagoId).map(d -> d.getId());
        });
        if (existing.isPresent()) {
            QuarkusTransaction.requiringNew().run(() -> billingProvider.setLagoInvoiceDocument(lagoId, existing.get()));
            return Map.of("document_id", existing.get().toString(), "already_issued", true);
        }
        Map<String, Object> spec = new HashMap<>();
        boolean factura = buyer.ruc() != null;
        spec.put("tipo_comprobante", factura ? "01" : "03");
        spec.put("moneda", invoice.path("currency").asString("PEN"));
        spec.put("cliente", factura
                ? Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", buyer.ruc(), "nombre", buyer.name())
                : Map.of("tipo_documento_identidad", "0", "numero_documento_identidad", "-", "nombre", buyer.name()));
        // Lago amounts are before IGV; the comprobante adds 18%. Coupons go as one net line.
        List<Map<String, Object>> detalles = new ArrayList<>();
        long feesCents = 0;
        for (JsonNode fee : invoice.path("fees")) {
            feesCents += fee.path("amount_cents").asLong();
        }
        long couponsCents = invoice.path("coupons_amount_cents").asLong(0);
        long netCents = feesCents - couponsCents;
        if (netCents <= 0) {
            return Map.of("skipped", "nothing to invoice");
        }
        if (couponsCents == 0) {
            for (JsonNode fee : invoice.path("fees")) {
                long cents = fee.path("amount_cents").asLong();
                if (cents > 0) {
                    detalles.add(Map.of("descripcion", fee.path("item").path("name").asString("Suscripción xhandler"), "cantidad", 1,
                            "precio", BigDecimal.valueOf(cents).movePointLeft(2)));
                }
            }
        } else {
            detalles.add(Map.of("descripcion", "Suscripción xhandler (con descuento)", "cantidad", 1,
                    "precio", BigDecimal.valueOf(netCents).movePointLeft(2)));
        }
        BigDecimal expected = BigDecimal.valueOf(netCents).movePointLeft(2).multiply(new BigDecimal("1.18")).setScale(2, RoundingMode.HALF_UP);
        if (invoice.has("total_amount_cents")) {
            BigDecimal charged = BigDecimal.valueOf(invoice.path("total_amount_cents").asLong()).movePointLeft(2);
            if (expected.subtract(charged).abs().compareTo(new BigDecimal("0.05")) > 0) {
                // A person must look: the comprobante must match what was charged
                throw new IllegalStateException("Lago invoice " + lagoId + " charged " + charged + " but its comprobante would total "
                        + expected + " (is the 18% IGV tax set up in Lago?)");
            }
        }
        if (!factura && expected.compareTo(new BigDecimal("700")) > 0) {
            throw new IllegalStateException("tenant " + customer + " has no billing RUC and invoice " + lagoId
                    + " is above S/ 700: a boleta must identify the buyer");
        }
        spec.put("detalles", detalles);
        spec.put("observaciones", "Factura Lago " + invoice.path("number").asString(lagoId));
        EmitManager.Emitted emitted = emit.emitAsPlatform(issuer.tenant(), issuer.company(), "lago:" + lagoId,
                new EmitRequestRepresentation(json.valueToTree(Map.of("kind", "Invoice", "spec", spec)), null, false,
                        Map.of("lago_invoice", lagoId, "tenant_id", customer.toString())),
                context.correlationId());
        UUID documentId = UUID.fromString(emitted.body().path("id").asString());
        QuarkusTransaction.requiringNew().run(() -> {
            billingProvider.setLagoInvoiceDocument(lagoId, documentId);
            if (buyer.email() != null) {
                TenantScope scope = new TenantScope(issuer.tenant(), Actor.PLATFORM, Role.PLATFORM);
                access.enterAsPlatform(issuer.tenant());
                jobs.enqueue(scope, DocumentEmailJob.NAME, 3, Map.of("document_id", documentId.toString(), "to", List.of(buyer.email()),
                        "cc", List.of(), "include_cdr", false), List.of(), context.correlationId());
            }
        });
        return Map.of("document_id", documentId.toString(), "serie_numero", emitted.body().path("serie_numero").asString());
    }
}
