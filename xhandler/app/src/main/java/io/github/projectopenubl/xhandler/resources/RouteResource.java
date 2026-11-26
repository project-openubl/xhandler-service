package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.RouteRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.RouteChangeRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.OseRepresentation;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.OseEndpointProvider;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.OseEndpointModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.managers.CredentialManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.clients.Egress;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * A company's submission route (DESIGN.md §3.2): SUNAT directly, or a third-party OSE. GRE always
 * goes to SUNAT. An OSE-obligated company can't choose SUNAT. OSE URLs are tenant-supplied hosts,
 * so they pass the egress checks (§16).
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/route")
public class RouteResource {

    private final ModelToRepresentation modelToRepresentation;

    private final CompanyManager companyManager;

    private final OseEndpointProvider oseEndpointProvider;




    private final TenantAccess access;
    private final CredentialManager credentials;
    private final Egress egress;
    private final AuditManager auditor;

    public RouteResource(TenantAccess access, CredentialManager credentials, Egress egress, AuditManager auditor, OseEndpointProvider oseEndpointProvider, CompanyManager companyManager, ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.companyManager = companyManager;
        this.oseEndpointProvider = oseEndpointProvider;
        this.access = access;
        this.credentials = credentials;
        this.egress = egress;
        this.auditor = auditor;
    }

    @Operation(operationId = "getRoute")
    @GET
    @Transactional
    public RouteRepresentation get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        return view(companyManager.getCompany(companyId));
    }

    @Operation(operationId = "setRoute")
    @PUT
    @Transactional
    public RouteRepresentation set(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid RouteChangeRepresentation change) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompanyForUpdate(companyId);
        if ("sunat".equals(change.submissionRoute())) {
            if (company.isOseObligated()) {
                throw ApiException.conflict("ose_obligated", "La empresa está obligada a emitir a través de un OSE.");
            }
        } else {
            OseRepresentation ose = change.ose();
            if (ose == null) {
                throw ApiException.invalid("invalid_request", "Indique los datos del OSE.", null);
            }
            egress.check(ose.invoiceUrl());
            if (ose.perceptionUrl() != null) {
                egress.check(ose.perceptionUrl());
            }
            OseEndpointModel endpoint = oseEndpointProvider.getOseEndpointByCompany(company.getId()).orElseGet(() -> {
                OseEndpointModel e = oseEndpointProvider.newOseEndpoint(Ids.newId());
                e.setTenantId(company.getTenantId());
                e.setCompanyId(company.getId());
                e.setCreatedAt(Instant.now());
                return e;
            });
            endpoint.setName(ose.name().strip());
            endpoint.setInvoiceUrl(ose.invoiceUrl());
            endpoint.setPerceptionUrl(ose.perceptionUrl());
            endpoint.setUpdatedAt(Instant.now());
            oseEndpointProvider.persist(endpoint);
            credentials.store(scope, company, "ose", Map.of("username", ose.username(), "password", ose.password()), ose.name());
        }
        company.setSubmissionRoute(change.submissionRoute());
        auditor.record(scope, "company.route_changed", "company", companyId, Map.of("route", change.submissionRoute()));
        return view(company);
    }

    RouteRepresentation view(CompanyModel company) {
        return modelToRepresentation.toRepresentation(company, oseEndpointProvider.getOseEndpointByCompany(company.getId()).orElse(null));
    }
}
