package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.AuditEntryRepresentation;
import io.github.projectopenubl.xhandler.models.AuditLogProvider;
import io.github.projectopenubl.xhandler.models.AuditLogModel;

import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** A tenant's audit log, newest first: tenants can see who accessed their data (DESIGN.md §26.1). */
@Path("/api/v1/tenants/{tenantId}/audit")
public class AuditResource {

    private final AuditLogProvider auditLogProvider;


    private final TenantAccess access;

    public AuditResource(TenantAccess access, AuditLogProvider auditLogProvider) {
        this.auditLogProvider = auditLogProvider;
        this.access = access;
    }

    @Operation(operationId = "listAuditEntries")
    @GET
    @Transactional
    public PageRepresentation<AuditEntryRepresentation> list(
            @PathParam("tenantId") UUID tenantId, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.ADMIN);
        int size = PageRepresentation.limit(limit);
        int from = PageRepresentation.offset(offset);
        List<AuditLogModel> entries = auditLogProvider.getAuditLogs(from, size);
        return new PageRepresentation<>(entries.stream()
                .map(e -> new AuditEntryRepresentation(e.getAt(), e.getActor(), e.getAction(), e.getObjectType(), e.getObjectId(), e.getDetails()))
                .toList(), size, from);
    }
}
