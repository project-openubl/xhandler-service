package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.SolCredentialsRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SmtpCredentialsRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SireCredentialsRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.LookupCredentialsRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.GreCredentialsRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CredentialRepresentation;
import io.github.projectopenubl.xhandler.models.CredentialProvider;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.managers.CredentialManager;
import io.github.projectopenubl.xhandler.models.CredentialModel;

import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** Company secrets. They can be written, but never read back through the API. */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/credentials")
public class CredentialResource {

    private final CredentialProvider credentialProvider;

    private final CompanyManager companyManager;



    private final TenantAccess access;
    private final CredentialManager credentials;
    private final io.github.projectopenubl.xhandler.clients.Egress egress;
    private final io.github.projectopenubl.xhandler.managers.EntitlementManager entitlements;
    private final io.github.projectopenubl.xhandler.managers.SireManager sire;

    public CredentialResource(TenantAccess access, CredentialManager credentials, io.github.projectopenubl.xhandler.clients.Egress egress,
            io.github.projectopenubl.xhandler.managers.EntitlementManager entitlements, io.github.projectopenubl.xhandler.managers.SireManager sire, CompanyManager companyManager, CredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
        this.companyManager = companyManager;
        this.sire = sire;
        this.entitlements = entitlements;
        this.access = access;
        this.credentials = credentials;
        this.egress = egress;
    }

    @Operation(operationId = "listCredentials")
    @GET
    @Transactional
    public List<CredentialRepresentation> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        CompanyModel company = companyManager.getCompany(companyId);
        List<CredentialModel> list = credentialProvider.getCredentials(company.getId());
        return list.stream().map(c -> new CredentialRepresentation(c.getKind(), c.getLabel(), c.getUpdatedAt())).toList();
    }

    @Operation(operationId = "setSolCredentials")
    @PUT
    @Path("/sol")
    @Transactional
    public CredentialRepresentation putSol(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid SolCredentialsRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompany(companyId);
        CredentialModel stored = credentials.store(scope, company, "sol",
                Map.of("username", request.username(), "password", request.password()), mask(request.username()));
        return new CredentialRepresentation(stored.getKind(), stored.getLabel(), Instant.now());
    }


    @Operation(operationId = "setGreCredentials")
    @PUT
    @Path("/gre")
    @Transactional
    public CredentialRepresentation putGre(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid GreCredentialsRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompany(companyId);
        CredentialModel stored = credentials.store(scope, company, "gre", Map.of("username", request.username(), "password",
                request.password(), "client_id", request.clientId(), "client_secret", request.clientSecret()), mask(request.username()));
        return new CredentialRepresentation(stored.getKind(), stored.getLabel(), Instant.now());
    }


    @Operation(operationId = "setSireCredentials")
    @PUT
    @Path("/sire")
    @Transactional
    public CredentialRepresentation putSire(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid SireCredentialsRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompany(companyId);
        CredentialModel stored = credentials.store(scope, company, "sire", Map.of("username", request.username(), "password",
                request.password(), "client_id", request.clientId(), "client_secret", request.clientSecret()), mask(request.username()));
        // SIRE periods waiting for credentials resume where they stopped
        sire.resumeAfterCredentials(company.getId());
        return new CredentialRepresentation(stored.getKind(), stored.getLabel(), Instant.now());
    }


    @Operation(operationId = "setSmtpCredentials")
    @PUT
    @Path("/smtp")
    @Transactional
    public CredentialRepresentation putSmtp(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid SmtpCredentialsRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompany(companyId);
        // A tenant-supplied host: it must resolve to public addresses (DESIGN.md §16)
        egress.checkHost(request.host());
        entitlements.requireCustomSmtp(company);
        CredentialModel stored = credentials.store(scope, company, "smtp", Map.of("host", request.host(), "port", String.valueOf(request.port()),
                "username", request.username(), "password", request.password(), "from", request.from(),
                "start_tls", String.valueOf(!Boolean.FALSE.equals(request.startTls()))), request.from());
        return new CredentialRepresentation(stored.getKind(), stored.getLabel(), Instant.now());
    }


    @Operation(operationId = "setLookupCredentials")
    @PUT
    @Path("/lookup")
    @Transactional
    public CredentialRepresentation putLookup(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid LookupCredentialsRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompany(companyId);
        egress.check(request.url().replace("{tipo}", "ruc").replace("{numero}", "20000000001"));
        Map<String, String> fields = new java.util.HashMap<>(Map.of("url", request.url()));
        if (request.token() != null) {
            fields.put("token", request.token());
        }
        CredentialModel stored = credentials.store(scope, company, "lookup", fields, java.net.URI.create(request.url().replace("{", "").replace("}", "")).getHost());
        return new CredentialRepresentation(stored.getKind(), stored.getLabel(), Instant.now());
    }

    /** "20123456789MODDATOS" → "2012…ATOS" */
    static String mask(String value) {
        return value.length() <= 8 ? "…" : value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }
}
