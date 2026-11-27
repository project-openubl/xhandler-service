package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.RevokedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EmissionModeRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CompanyRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.AuthorizationRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.AuthorizationRepresentation;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.CompanyModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.managers.NotificationManager;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.models.PseProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * PSE mode of a company (DESIGN.md §1.1, §3.2): the tenant authorizes the platform in SUNAT's SOL
 * and registers that authorization here (with evidence); then the platform signs with its PSE
 * certificate and sends with its own SOL users. Revoking it is notified (§26.6).
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/pse")
public class PseResource {

    private final ModelToRepresentation modelToRepresentation;

    private final CompanyManager companyManager;




    private final TenantAccess access;
    private final PseProvider pseProvider;
    private final AuditManager auditor;
    private final FileStore files;
    private final NotificationManager notifications;
    private final PlatformKeys platformKeys;

    public PseResource(TenantAccess access, PseProvider pseProvider, AuditManager auditor, FileStore files, NotificationManager notifications,
            PlatformKeys platformKeys, CompanyManager companyManager, ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.companyManager = companyManager;
        this.access = access;
        this.pseProvider = pseProvider;
        this.auditor = auditor;
        this.files = files;
        this.notifications = notifications;
        this.platformKeys = platformKeys;
    }

    @Operation(operationId = "listPseAuthorizations")
    @GET
    @Transactional
    public List<AuthorizationRepresentation> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        companyManager.getCompany(companyId);
        return pseProvider.getAuthorizations(companyId).stream().map(r -> new AuthorizationRepresentation(r.id(), r.authorizedAt(), r.evidence(),
                r.registeredBy(), r.approvedAt(), r.revokedAt())).toList();
    }

    /** Registers the SOL authorization; the evidence (a PDF or image of SOL's confirmation) is kept. */
    @Operation(operationId = "authorizePse")
    @POST
    @Path("/authorization")
    @Transactional
    public AuthorizationRepresentation authorize(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Valid AuthorizationRequestRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompanyForUpdate(companyId);
        if (company.getRucVerifiedAt() == null) {
            throw ApiException.conflict("ruc_not_verified", "Verifique el RUC de la empresa antes de registrar la autorización de PSE.");
        }
        if (request.authorizedAt().isAfter(io.github.projectopenubl.xhandler.utils.Deadlines.todayInLima())) {
            throw ApiException.invalid("invalid_request", "La fecha de autorización no puede ser futura.", null);
        }
        if (pseProvider.hasActiveAuthorization(companyId)) {
            throw ApiException.conflict("already_authorized", "La empresa ya tiene una autorización vigente.");
        }
        // Evidence of the SOL authorization (a PDF or image), reviewed by a platform admin before it counts
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(request.evidenceBase64());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("invalid_request", "La evidencia debe estar en base64.", null);
        }
        if (bytes.length > 2 * 1024 * 1024) {
            throw ApiException.invalid("invalid_request", "La evidencia no debe pasar de 2 MB.", null);
        }
        boolean pdf = bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        boolean png = bytes.length > 4 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpeg = bytes.length > 3 && (bytes[0] & 0xff) == 0xFF && (bytes[1] & 0xff) == 0xD8;
        if (!pdf && !png && !jpeg) {
            throw ApiException.invalid("invalid_request", "La evidencia debe ser un PDF, PNG o JPEG.", null);
        }
        String evidence = files.put(company.getTenantId(), bytes).key();
        UUID id = Ids.newId();
        pseProvider.addAuthorization(id, tenantId, companyId, request.authorizedAt(), evidence, scope.actor().auditName());
        auditor.record(scope, "pse.authorization_registered", "company", companyId, Map.of("authorized_at", request.authorizedAt().toString()));
        return new AuthorizationRepresentation(id, request.authorizedAt(), true, scope.actor().auditName(), null, null);
    }


    /** The tenant revoked the platform in SOL: PSE stops, the company goes back to software mode. */
    @Operation(operationId = "revokePseAuthorization")
    @DELETE
    @Path("/authorization")
    @Transactional
    public RevokedRepresentation revoke(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompanyForUpdate(companyId);
        long pending = pseProvider.countPendingPseDocuments(companyId);
        if (pending > 0) {
            // The platform must finish what it signed as PSE before it stops being the company's PSE
            throw ApiException.conflict("pse_documents_pending",
                    "Hay " + pending + " documentos enviados como PSE sin respuesta definitiva. Revoque cuando terminen.");
        }
        int revoked = pseProvider.revokeAuthorizations(companyId, scope.actor().auditName());
        if (revoked == 0) {
            throw ApiException.notFound();
        }
        company.setEmissionMode("software");
        auditor.record(scope, "pse.authorization_revoked", "company", companyId);
        notifications.publish(tenantId, NotificationManager.PSE_AUTHORIZATION, "Autorización de PSE revocada: " + company.getRuc(),
                "La empresa " + company.getRazonSocial() + " dejó de autorizar a la plataforma como PSE. Sus documentos se firmarán con su propio "
                        + "certificado y se enviarán con su usuario SOL.", null);
        return new RevokedRepresentation(companyId, company.getEmissionMode());
    }

    @Operation(operationId = "setEmissionMode")
    @PUT
    @Path("/mode")
    @Transactional
    public CompanyRepresentation mode(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Valid EmissionModeRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompanyForUpdate(companyId);
        if ("pse".equals(request.emissionMode())) {
            if (!pseProvider.hasApprovedAuthorization(companyId)) {
                throw ApiException.conflict("pse_not_authorized", "La autorización a la plataforma como PSE no está registrada o aún no fue aprobada.");
            }
            if ("production".equals(company.getSunatEnv()) && platformKeys.active("pse").isEmpty()) {
                throw ApiException.conflict("pse_unavailable", "La plataforma aún no opera como PSE en producción.");
            }
        }
        company.setEmissionMode(request.emissionMode());
        auditor.record(scope, "company.emission_mode_changed", "company", companyId, Map.of("mode", request.emissionMode()));
        return modelToRepresentation.toRepresentation(company);
    }
}
