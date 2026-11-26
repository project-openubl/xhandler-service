package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.EmailProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Addresses a tenant no longer emails (DESIGN.md §26.5): hard bounces and complaints, recorded by
 * the platform's bounce processing; the tenant sees them and may remove one after fixing it.
 */
@Path("/api/v1/tenants/{tenantId}/email-suppressions")
public class EmailSuppressions {

    public record Suppression(String address, String reason, Instant createdAt) {}

    private final TenantAccess access;
    private final EmailProvider emailProvider;
    private final AuditManager auditor;

    public EmailSuppressions(TenantAccess access, EmailProvider emailProvider, AuditManager auditor) {
        this.access = access;
        this.emailProvider = emailProvider;
        this.auditor = auditor;
    }

    @Operation(operationId = "listEmailSuppressions")
    @GET
    @Transactional
    public List<Suppression> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        return emailProvider.getEmailSuppressions().stream().map(r -> new Suppression(r.address(), r.reason(), r.createdAt())).toList();
    }

    @APIResponse(responseCode = "204", description = "Dirección quitada de la lista de supresión.")
    @Operation(operationId = "removeEmailSuppression")
    @DELETE
    @Path("/{address}")
    @Transactional
    public Response remove(@PathParam("tenantId") UUID tenantId, @PathParam("address") String address) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        if (!emailProvider.deleteEmailSuppression(address)) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "email.suppression_removed", "email", null, Map.of("address", normalize(address)));
        return Response.noContent().build();
    }


    static String normalize(String address) {
        return address.strip().toLowerCase(Locale.ROOT);
    }
}
