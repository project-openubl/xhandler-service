package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.utils.RepresentationToModel;
import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.WebhookRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DeliveryRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateWebhookRepresentation;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.WebhookSubscriptionProvider;
import io.github.projectopenubl.xhandler.models.WebhookDeliveryProvider;
import io.github.projectopenubl.xhandler.clients.Egress;
import io.github.projectopenubl.xhandler.events.WebhookEvents;
import io.github.projectopenubl.xhandler.models.WebhookDeliveryModel;
import io.github.projectopenubl.xhandler.models.WebhookSubscriptionModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.managers.IdempotencyManager;
import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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

    private final RepresentationToModel representationToModel;

    private final ModelToRepresentation modelToRepresentation;

    private final CompanyProvider companyProvider;

    private final WebhookSubscriptionProvider webhookSubscriptionProvider;

    private final WebhookDeliveryProvider webhookDeliveryProvider;




    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantAccess access;
    private final SecretBox box;
    private final Egress egress;
    private final WebhookEvents events;
    private final AuditManager auditor;
    private final io.github.projectopenubl.xhandler.managers.EntitlementManager entitlements;
    private final IdempotencyManager idempotency;

    public WebhookResource(TenantAccess access, SecretBox box, Egress egress, WebhookEvents events, AuditManager auditor,
            io.github.projectopenubl.xhandler.managers.EntitlementManager entitlements, IdempotencyManager idempotency, WebhookDeliveryProvider webhookDeliveryProvider, WebhookSubscriptionProvider webhookSubscriptionProvider, CompanyProvider companyProvider, ModelToRepresentation modelToRepresentation, RepresentationToModel representationToModel) {
        this.representationToModel = representationToModel;
        this.modelToRepresentation = modelToRepresentation;
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
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = WebhookRepresentation.class)))
    @APIResponse(responseCode = "409", description = "Idempotency-Key en proceso, o RUC sin verificar.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createWebhook")
    @POST
    public Response create(@PathParam("tenantId") UUID tenantId, @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid CreateWebhookRepresentation request) {
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
            representationToModel.updateWebhookSubscription(request, subscription);
            subscription.seal(box.seal(secret.getBytes(StandardCharsets.UTF_8), subscription.context()));
            subscription.setCreatedAt(Instant.now());
            subscription.setUpdatedAt(subscription.getCreatedAt());
            webhookSubscriptionProvider.persist(subscription);
            auditor.record(scope, "webhook.created", "webhook", subscription.getId(), Map.of("url", request.url()));
            return modelToRepresentation.toRepresentation(subscription, secret);
        }, view -> new WebhookRepresentation(view.id(), view.url(), view.events(), view.active(), view.createdAt(), null));
    }

    @Operation(operationId = "listWebhooks")
    @GET
    @Transactional
    public List<WebhookRepresentation> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        List<WebhookSubscriptionModel> subscriptions = webhookSubscriptionProvider.getWebhookSubscriptions();
        return subscriptions.stream().map(s -> modelToRepresentation.toRepresentation(s, null)).toList();
    }

    @Operation(operationId = "getWebhook")
    @GET
    @Path("/{webhookId}")
    @Transactional
    public WebhookRepresentation get(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId) {
        access.enter(tenantId, Role.VIEWER);
        return modelToRepresentation.toRepresentation(find(webhookId), null);
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
    public DeliveryRepresentation test(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId) {
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
        return modelToRepresentation.toRepresentation(delivery);
    }

    @Operation(operationId = "listWebhookDeliveries")
    @GET
    @Path("/{webhookId}/deliveries")
    @Transactional
    public PageRepresentation<DeliveryRepresentation> deliveries(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId,
            @QueryParam("status") String status, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        find(webhookId);
        int size = PageRepresentation.limit(limit);
        int start = PageRepresentation.offset(offset);
        List<WebhookDeliveryModel> page = webhookDeliveryProvider.getWebhookDeliveries(webhookId, status, start, size);
        return new PageRepresentation<>(page.stream().map(modelToRepresentation::toRepresentation).toList(), size, start);
    }

    @Operation(operationId = "redeliverWebhook")
    @POST
    @Path("/{webhookId}/deliveries/{deliveryId}/redeliver")
    @Transactional
    public DeliveryRepresentation redeliver(@PathParam("tenantId") UUID tenantId, @PathParam("webhookId") UUID webhookId,
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
        return modelToRepresentation.toRepresentation(delivery);
    }

    private WebhookSubscriptionModel find(UUID id) {
        WebhookSubscriptionModel subscription = webhookSubscriptionProvider.getWebhookSubscription(id).orElse(null);
        if (subscription == null || subscription.getDeletedAt() != null) {
            throw ApiException.notFound();
        }
        return subscription;
    }


}
