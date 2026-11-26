package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.BillingRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.BillingDetailsRepresentation;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.managers.EntitlementManager;

import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** A tenant's plan, usage and billing details (DESIGN.md §14). */
@Path("/api/v1/tenants/{tenantId}/billing")
public class BillingResource {

    private final TenantProvider tenantProvider;



    private final TenantAccess access;
    private final EntitlementManager entitlements;
    private final AuditManager auditor;

    public BillingResource(TenantAccess access, EntitlementManager entitlements, AuditManager auditor, TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.access = access;
        this.entitlements = entitlements;
        this.auditor = auditor;
    }

    @Operation(operationId = "getBilling")
    @GET
    @Transactional
    public BillingRepresentation get(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        return new BillingRepresentation(entitlements.usage(tenantId), tenant.getBillingRuc(), tenant.getBillingName(), tenant.getBillingEmail());
    }

    @Operation(operationId = "updateBillingDetails")
    @PUT
    @Transactional
    public BillingRepresentation details(@PathParam("tenantId") UUID tenantId, @Valid BillingDetailsRepresentation details) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        TenantModel tenant = tenantProvider.getTenantForUpdate(tenantId).orElse(null);
        tenant.setBillingRuc(details.billingRuc());
        tenant.setBillingName(details.billingName());
        tenant.setBillingEmail(details.billingEmail());
        auditor.record(scope, "tenant.billing_updated", "tenant", tenantId);
        return get(tenantId);
    }
}
