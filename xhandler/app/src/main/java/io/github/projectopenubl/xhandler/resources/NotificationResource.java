package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.representations.idm.Page;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.resources.StatusResource;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.models.NotificationProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** In-app notifications and their preferences (DESIGN.md §26.6). */
@Path("/api/v1/tenants/{tenantId}")
public class NotificationResource {

    private final TenantProvider tenantProvider;

    public record NotificationView(UUID id, String kind, String title, String body, UUID documentId, Instant createdAt, Instant readAt) {}

    public record Preference(@NotBlank String kind, boolean email, boolean inApp) {}

    public record NotificationSettings(@Email String notificationEmail) {}

    private final TenantAccess access;
    private final NotificationProvider notificationProvider;
    private final Auditor auditor;

    public NotificationResource(TenantAccess access, NotificationProvider notificationProvider, Auditor auditor, TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.access = access;
        this.notificationProvider = notificationProvider;
        this.auditor = auditor;
    }

    @Operation(operationId = "listNotifications")
    @GET
    @Path("/notifications")
    @Transactional
    public Page<NotificationView> list(@PathParam("tenantId") UUID tenantId, @QueryParam("unread") Boolean unread,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = Page.limit(limit);
        int from = Page.offset(offset);
        return new Page<>(notificationProvider.getNotifications(Boolean.TRUE.equals(unread), size, from).stream()
                .map(r -> new NotificationView(r.id(), r.kind(), r.title(), r.body(), r.documentId(), r.createdAt(), r.readAt())).toList(),
                size, from);
    }

    @APIResponse(responseCode = "204", description = "Notificación marcada como leída.")
    @Operation(operationId = "markNotificationRead")
    @POST
    @Path("/notifications/{id}/read")
    @Transactional
    public Response read(@PathParam("tenantId") UUID tenantId, @PathParam("id") UUID id) {
        access.enter(tenantId, Role.VIEWER);
        if (!notificationProvider.markNotificationRead(id)) {
            throw ApiException.notFound();
        }
        return Response.noContent().build();
    }

    @APIResponse(responseCode = "204", description = "Notificaciones marcadas como leídas.")
    @Operation(operationId = "markAllNotificationsRead")
    @POST
    @Path("/notifications/read-all")
    @Transactional
    public Response readAll(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        notificationProvider.markAllNotificationsRead();
        return Response.noContent().build();
    }

    @Operation(operationId = "getNotificationPreferences")
    @GET
    @Path("/notification-preferences")
    @Transactional
    public List<Preference> preferences(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        return notificationProvider.getNotificationPreferences().stream().map(r -> new Preference(r.kind(), r.email(), r.inApp())).toList();
    }

    @Operation(operationId = "setNotificationPreferences")
    @PUT
    @Path("/notification-preferences")
    @Transactional
    public List<Preference> setPreferences(@PathParam("tenantId") UUID tenantId, List<@Valid Preference> preferences) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        for (Preference p : preferences) {
            notificationProvider.setNotificationPreference(tenantId, p.kind(), p.email(), p.inApp());
        }
        auditor.record(scope, "notification.preferences_updated", "tenant", tenantId);
        return preferences(tenantId);
    }

    @Operation(operationId = "getNotificationSettings", summary = "Correo al que se envían las notificaciones de la cuenta")
    @GET
    @Path("/notification-settings")
    @Transactional
    public NotificationSettings getSettings(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        if (tenant == null) {
            throw ApiException.notFound();
        }
        return new NotificationSettings(tenant.getNotificationEmail());
    }

    @Operation(operationId = "updateNotificationSettings")
    @PUT
    @Path("/notification-settings")
    @Transactional
    public NotificationSettings settings(@PathParam("tenantId") UUID tenantId, @Valid NotificationSettings settings) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        TenantModel tenant = tenantProvider.getTenantForUpdate(tenantId).orElse(null);
        tenant.setNotificationEmail(settings.notificationEmail() == null || settings.notificationEmail().isBlank()
                ? null : settings.notificationEmail().strip());
        auditor.record(scope, "tenant.notification_email_changed", "tenant", tenantId);
        return new NotificationSettings(tenant.getNotificationEmail());
    }
}
