package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.managers.EntitlementManager;
import io.github.projectopenubl.xhandler.jobs.handlers.SelfInvoicingJob;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.jobs.JobStore;
import io.github.projectopenubl.xhandler.models.BillingProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Lago's webhook endpoint; public (Lago calls it), authenticated by its HMAC signature. */
@org.eclipse.microprofile.openapi.annotations.tags.Tag(name = "Webhook")
@Path("/api/v1/public/lago/webhooks")
public class LagoWebhookResource {

    private final Optional<String> secret;
    private final JobStore jobs;
    private final BillingProvider billingProvider;
    private final ObjectMapper json;
    private final TenantAccess access;
    private final EntitlementManager entitlements;

    public LagoWebhookResource(@ConfigProperty(name = "xhandler.lago.webhook-secret") Optional<String> secret, JobStore jobs, BillingProvider billingProvider,
            ObjectMapper json, TenantAccess access, EntitlementManager entitlements) {
        this.access = access;
        this.entitlements = entitlements;
        this.secret = secret;
        this.jobs = jobs;
        this.billingProvider = billingProvider;
        this.json = json;
    }

    @APIResponse(responseCode = "204", description = "Evento recibido.")
    @Operation(operationId = "receiveLagoWebhook")
    @POST
    public Response receive(@HeaderParam("X-Lago-Signature") String signature, byte[] body) throws Exception {
        if (secret.isEmpty() || signature == null || !validSignature(secret.get(), body, signature)) {
            throw new ApiException(401, "invalid_signature", "Firma inválida.");
        }
        JsonNode event = json.readTree(body);
        String type = event.path("webhook_type").asString();
        if (type.startsWith("subscription.")) {
            // Plans follow Lago's subscriptions as they change; the periodic sync is the safety net
            JsonNode subscription = event.path("subscription");
            UUID tenant = tenantId(subscription.path("external_customer_id").asString());
            String plan = "terminated".equals(subscription.path("status").asString()) ? "free" : subscription.path("plan_code").asString();
            QuarkusTransaction.requiringNew().run(() -> {
                access.enterAsPlatform(tenant);
                if (entitlements.known(plan)) {
                    entitlements.setPlan(tenant, plan, "lago");
                }
            });
            return Response.noContent().build();
        }
        if (!"invoice.created".equals(type)) {
            return Response.noContent().build();
        }
        JsonNode invoice = event.path("invoice");
        String lagoId = invoice.path("lago_id").asString();
        UUID tenant = tenantId(invoice.path("customer").path("external_id").asString());
        QuarkusTransaction.requiringNew().run(() -> {
            if (billingProvider.addLagoInvoice(lagoId, tenant)) {
                jobs.enqueuePlatform(SelfInvoicingJob.NAME, 4, Map.of("lago_invoice_id", lagoId, "tenant_id", tenant.toString(),
                        "invoice", invoice.toString()), List.of("lago-invoice:" + lagoId));
            }
        });
        return Response.noContent().build();
    }

    static UUID tenantId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("invalid_request", "external_id no es un tenant.", null);
        }
    }

    /** Lago's HMAC mode: base64(HMAC-SHA256(body, secret)). */
    static boolean validSignature(String secret, byte[] body, String signature) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = Base64.getEncoder().encode(mac.doFinal(body));
        return MessageDigest.isEqual(expected, signature.strip().getBytes(StandardCharsets.UTF_8));
    }
}
