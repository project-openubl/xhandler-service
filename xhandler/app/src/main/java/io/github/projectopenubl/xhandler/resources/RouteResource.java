package io.github.projectopenubl.xhandler.resources;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    private final CompanyManager companyManager;

    private final OseEndpointProvider oseEndpointProvider;

    public record Ose(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 2000) String invoiceUrl,
            @Size(max = 2000) String perceptionUrl, @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}

    public record RouteChange(@NotBlank @Pattern(regexp = "sunat|ose") String submissionRoute, @Valid Ose ose) {}

    public record RouteView(String submissionRoute, boolean oseObligated, String oseName, String oseInvoiceUrl, String osePerceptionUrl) {}

    private final TenantAccess access;
    private final CredentialManager credentials;
    private final Egress egress;
    private final AuditManager auditor;

    public RouteResource(TenantAccess access, CredentialManager credentials, Egress egress, AuditManager auditor, OseEndpointProvider oseEndpointProvider, CompanyManager companyManager) {
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
    public RouteView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        return view(companyManager.getCompany(companyId));
    }

    @Operation(operationId = "setRoute")
    @PUT
    @Transactional
    public RouteView set(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid RouteChange change) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompanyForUpdate(companyId);
        if ("sunat".equals(change.submissionRoute())) {
            if (company.isOseObligated()) {
                throw ApiException.conflict("ose_obligated", "La empresa está obligada a emitir a través de un OSE.");
            }
        } else {
            Ose ose = change.ose();
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

    RouteView view(CompanyModel company) {
        OseEndpointModel ose = oseEndpointProvider.getOseEndpointByCompany(company.getId()).orElse(null);
        return new RouteView(company.getSubmissionRoute(), company.isOseObligated(), ose == null ? null : ose.getName(),
                ose == null ? null : ose.getInvoiceUrl(), ose == null ? null : ose.getPerceptionUrl());
    }
}
