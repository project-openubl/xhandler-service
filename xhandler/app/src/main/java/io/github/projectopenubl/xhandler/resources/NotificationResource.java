package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.PreferenceRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.NotificationSettingsRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.NotificationRepresentation;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.models.NotificationProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** In-app notifications and their preferences (DESIGN.md §26.6). */
@Path("/api/v1/tenants/{tenantId}")
public class NotificationResource {

    private final TenantProvider tenantProvider;




    private final TenantAccess access;
    private final NotificationProvider notificationProvider;
    private final AuditManager auditor;

    public NotificationResource(TenantAccess access, NotificationProvider notificationProvider, AuditManager auditor, TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.access = access;
        this.notificationProvider = notificationProvider;
        this.auditor = auditor;
    }

    @Operation(operationId = "listNotifications")
    @GET
    @Path("/notifications")
    @Transactional
    public PageRepresentation<NotificationRepresentation> list(@PathParam("tenantId") UUID tenantId, @QueryParam("unread") Boolean unread,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = PageRepresentation.limit(limit);
        int from = PageRepresentation.offset(offset);
        return new PageRepresentation<>(notificationProvider.getNotifications(Boolean.TRUE.equals(unread), size, from).stream()
                .map(r -> new NotificationRepresentation(r.id(), r.kind(), r.title(), r.body(), r.documentId(), r.createdAt(), r.readAt())).toList(),
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
    public List<PreferenceRepresentation> preferences(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        return notificationProvider.getNotificationPreferences().stream().map(r -> new PreferenceRepresentation(r.kind(), r.email(), r.inApp())).toList();
    }

    @Operation(operationId = "setNotificationPreferences")
    @PUT
    @Path("/notification-preferences")
    @Transactional
    public List<PreferenceRepresentation> setPreferences(@PathParam("tenantId") UUID tenantId, List<@Valid PreferenceRepresentation> preferences) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        for (PreferenceRepresentation p : preferences) {
            notificationProvider.setNotificationPreference(tenantId, p.kind(), p.email(), p.inApp());
        }
        auditor.record(scope, "notification.preferences_updated", "tenant", tenantId);
        return preferences(tenantId);
    }

    @Operation(operationId = "getNotificationSettings", summary = "Correo al que se envían las notificaciones de la cuenta")
    @GET
    @Path("/notification-settings")
    @Transactional
    public NotificationSettingsRepresentation getSettings(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        if (tenant == null) {
            throw ApiException.notFound();
        }
        return new NotificationSettingsRepresentation(tenant.getNotificationEmail());
    }

    @Operation(operationId = "updateNotificationSettings")
    @PUT
    @Path("/notification-settings")
    @Transactional
    public NotificationSettingsRepresentation settings(@PathParam("tenantId") UUID tenantId, @Valid NotificationSettingsRepresentation settings) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        TenantModel tenant = tenantProvider.getTenantForUpdate(tenantId).orElse(null);
        tenant.setNotificationEmail(settings.notificationEmail() == null || settings.notificationEmail().isBlank()
                ? null : settings.notificationEmail().strip());
        auditor.record(scope, "tenant.notification_email_changed", "tenant", tenantId);
        return new NotificationSettingsRepresentation(tenant.getNotificationEmail());
    }
}
