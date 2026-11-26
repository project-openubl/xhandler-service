package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.WebhookSubscriptionProvider;
import io.github.projectopenubl.xhandler.models.WebhookDeliveryProvider;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.clients.Egress;
import io.github.projectopenubl.xhandler.events.WebhookEvents;
import io.github.projectopenubl.xhandler.models.WebhookDeliveryModel;
import io.github.projectopenubl.xhandler.models.WebhookSubscriptionModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.Page;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.managers.Idempotency;
import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Webhook subscriptions, delivery logs, redelivery and test events (DESIGN.md §12, §26.2). The
 * signing secret is returned once, at creation.
 */
@Path("/api/v1/tenants/{tenantId}/webhooks")
public class WebhookResource {

    private final CompanyProvider companyProvider;

    private final WebhookSubscriptionProvider webhookSubscriptionProvider;

    private final WebhookDeliveryProvider webhookDeliveryProvider;

    public record CreateWebhook(@NotBlank @Size(max = 2000) String url, @NotEmpty List<@NotBlank String> events) {}

    public record WebhookView(UUID id, String url, List<String> events, boolean active, Instant createdAt, String secret) {}

    public record DeliveryView(UUID id, UUID eventId, String eventType, String status, int attempts, Instant nextAttemptAt,
            Integer lastStatusCode, String lastError, Instant deliveredAt, Instant createdAt) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantAccess access;
    private final SecretBox box;
    private final Egress egress;
    private final WebhookEvents events;
    private final Auditor auditor;
    private final io.github.projectopenubl.xhandler.managers.Entitlements entitlements;
    private final Idempotency idempotency;

    public WebhookResource(TenantAccess access, SecretBox box, Egress egress, WebhookEvents events, Auditor auditor,
            io.github.projectopenubl.xhandler.managers.Entitlements entitlements, Idempotency idempotency, WebhookDeliveryProvider webhookDeliveryProvider, WebhookSubscriptionProvider webhookSubscriptionProvider, CompanyProvider companyProvider) {
        this.companyProvider = companyProvider;
        this.webhookSubscriptionProvider = webhookSubscriptionProvider;
        this.webhookDeliveryProvider = webhookDeliveryProvider;
        this.entitlements = entitlements;
        this.idempotency = idempotency;
        this.access = access;
        this.box = box;
        this.egress = egress;
        this.events = events;
        this.auditor = auditor;
    }

    @APIResponse(responseCode = "201", description = "Webhook creado; el secreto se muestra solo esta vez (la respuesta repetida de una "
            + "Idempotency-Key ya usada no lo trae).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = WebhookView.class)))
    @APIResponse(responseCode = "409", description = "Idempotency-Key en proceso, o RUC sin verificar.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createWebhook")
    @POST
    public Response create(@PathParam("tenantId") UUID tenantId, @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid CreateWebhook request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("webhook", tenantId, request), () -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            if (!entitlements.usage(tenantId).plan().webhooks()) {
                throw new ApiException(402, "plan_required", "Su plan no incluye webhooks.");
            }
            // Free tenants without a verified RUC can't point webhooks at arbitrary hosts (§26.8)
            if ("free".equals(entitlements.usage(tenantId).plan().code())
                    && companyProvider.countVerifiedCompanies() == 0) {
                throw ApiException.conflict("ruc_not_verified", "Verifique el RUC de una empresa para usar webhooks.");
            }
            egress.check(request.url());
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            WebhookSubscriptionModel subscription = webhookSubscriptionProvider.newWebhookSubscription(Ids.newId());
            subscription.setTenantId(tenantId);
            subscription.setUrl(request.url());
            subscription.setEvents(request.events().toArray(String[]::new));
            subscription.seal(box.seal(secret.getBytes(StandardCharsets.UTF_8), subscription.context()));
            subscription.setCreatedAt(Instant.now());
            subscription.setUpdatedAt(subscription.getCreatedAt());
            webhookSubscriptionProvider.persist(subscription);
            auditor.record(scope, "webhook.created", "webhook", subscription.getId(), Map.of("url", request.url()));
            return view(subscription, secret);
        }, view -> new WebhookView(view.id(), view.url(), view.events(), view.active(), view.createdAt(), null));
    }

    @Operation(operationId = "listWebhooks")
    @GET
    @Transactional
    public List<WebhookView> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        List<WebhookSubscriptionModel> subscriptions = webhookSubscriptionProvider.getWebhookSubscriptions();
        return subscriptions.stream().map(s -> view(s, null)).toList();
    }

    @Operation(operationId = "getWebhook")
    @GET
    @Path("/{webhookId}")
    @Transactional
    public WebhookView get(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId) {
        access.enter(tenantId, Role.VIEWER);
        return view(find(webhookId), null);
    }

    @APIResponse(responseCode = "204", description = "Webhook eliminado.")
    @Operation(operationId = "deleteWebhook")
    @DELETE
    @Path("/{webhookId}")
    @Transactional
    public Response delete(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        WebhookSubscriptionModel subscription = find(webhookId);
        subscription.setDeletedAt(Instant.now());
        subscription.setActive(false);
        auditor.record(scope, "webhook.deleted", "webhook", webhookId);
        return Response.noContent().build();
    }

    /** Sends a {@code webhook.test} event to this subscription only (webhook tools, §26.2). */
    @Operation(operationId = "testWebhook")
    @POST
    @Path("/{webhookId}/test")
    @Transactional
    public DeliveryView test(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        WebhookSubscriptionModel subscription = find(webhookId);
        auditor.record(scope, "webhook.test_sent", "webhook", webhookId);
        Map<String, Object> event = Map.of("specversion", "1.0", "id", Ids.newId().toString(), "source", WebhookEvents.SOURCE,
                "type", "webhook.test", "subject", "xh:webhook:" + webhookId, "time", Instant.now().toString(),
                "datacontenttype", "application/json", "data", Map.of("message", "Evento de prueba de xhandler"));
        WebhookDeliveryModel delivery = webhookDeliveryProvider.newWebhookDelivery(Ids.newId());
        delivery.setTenantId(tenantId);
        delivery.setSubscriptionId(subscription.getId());
        delivery.setEventId(UUID.fromString((String) event.get("id")));
        delivery.setEventType("webhook.test");
        delivery.setPayload(event);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setCreatedAt(delivery.getNextAttemptAt());
        delivery.setUpdatedAt(delivery.getNextAttemptAt());
        webhookDeliveryProvider.persist(delivery);
        return view(delivery);
    }

    @Operation(operationId = "listWebhookDeliveries")
    @GET
    @Path("/{webhookId}/deliveries")
    @Transactional
    public Page<DeliveryView> deliveries(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId,
            @QueryParam("status") String status, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        find(webhookId);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        List<WebhookDeliveryModel> page = webhookDeliveryProvider.getWebhookDeliveries(webhookId, status, start, size);
        return new Page<>(page.stream().map(WebhookResource::view).toList(), size, start);
    }

    @Operation(operationId = "redeliverWebhook")
    @POST
    @Path("/{webhookId}/deliveries/{deliveryId}/redeliver")
    @Transactional
    public DeliveryView redeliver(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId,
            @PathParam("deliveryId") UUID deliveryId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        WebhookDeliveryModel delivery = webhookDeliveryProvider.getWebhookDeliveryForUpdate(deliveryId).orElse(null);
        if (delivery == null || !delivery.getSubscriptionId().equals(webhookId)) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "webhook.redelivered", "webhook_delivery", deliveryId);
        delivery.setStatus("pending");
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setUpdatedAt(delivery.getNextAttemptAt());
        return view(delivery);
    }

    private WebhookSubscriptionModel find(UUID id) {
        WebhookSubscriptionModel subscription = webhookSubscriptionProvider.getWebhookSubscription(id).orElse(null);
        if (subscription == null || subscription.getDeletedAt() != null) {
            throw ApiException.notFound();
        }
        return subscription;
    }

    private static WebhookView view(WebhookSubscriptionModel s, String secret) {
        return new WebhookView(s.getId(), s.getUrl(), List.of(s.getEvents()), s.isActive(), s.getCreatedAt(), secret);
    }

    private static DeliveryView view(WebhookDeliveryModel d) {
        return new DeliveryView(d.getId(), d.getEventId(), d.getEventType(), d.getStatus(), d.getAttempts(), d.getNextAttemptAt(), d.getLastStatusCode(), d.getLastError(),
                d.getDeliveredAt(), d.getCreatedAt());
    }
}
