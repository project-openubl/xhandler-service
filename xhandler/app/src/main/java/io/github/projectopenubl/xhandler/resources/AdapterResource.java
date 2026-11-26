package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.TokenRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SfsRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.NubefactTokenRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EmitRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.AdapterTokenRepresentation;
import io.github.projectopenubl.xhandler.utils.Tokens;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.AdapterTokenProvider;
import io.github.projectopenubl.xhandler.managers.UblUploadManager;
import io.github.projectopenubl.xhandler.utils.SfsAdapter;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.representations.idm.DocumentRepresentation;
import io.github.projectopenubl.xhandler.managers.EmitManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Inputs other than native JSON (DESIGN.md §5): UBL XML upload, SFS TXT, and tokens for the Nubefact-compatible API. */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}")
public class AdapterResource {

    private final CompanyManager companyManager;




    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantAccess access;
    private final UblUploadManager ubl;
    private final EmitManager emit;
    private final AdapterTokenProvider adapterTokens;
    private final AuditManager auditor;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public AdapterResource(TenantAccess access, UblUploadManager ubl, EmitManager emit, AdapterTokenProvider adapterTokens, AuditManager auditor,
            com.fasterxml.jackson.databind.ObjectMapper json, CompanyManager companyManager) {
        this.companyManager = companyManager;
        this.access = access;
        this.ubl = ubl;
        this.emit = emit;
        this.adapterTokens = adapterTokens;
        this.auditor = auditor;
        this.json = json;
    }

    /** A UBL XML built elsewhere: validated, signed if unsigned, stored and sent. */
    @APIResponse(responseCode = "201", description = "Documento recibido y emitido.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DocumentRepresentation.class)))
    @Operation(operationId = "uploadUblDocument")
    @POST
    @Path("/documents/ubl")
    @Consumes({"application/xml", "text/xml"})
    public Response uploadUbl(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("X-Correlation-Id") String correlationId, byte[] xml) {
        DocumentRepresentation view = ubl.upload(tenantId, companyId, xml, correlationId == null ? UUID.randomUUID().toString() : correlationId);
        return Response.status(201).entity(view).build();
    }

    /** SUNAT SFS TXT files (.CAB/.NOT + .DET) converted to native JSON; the document keeps its number. */
    @APIResponse(responseCode = "201", description = "Documento emitido (también la respuesta repetida de una Idempotency-Key ya usada, sin access_url).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DocumentRepresentation.class)))
    @APIResponse(responseCode = "402", description = "Sin cuota de documentos en el plan (quota_exceeded).",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "409", description = "Conflicto: Idempotency-Key en proceso, número ya usado, empresa desactivada o no autorizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "422", description = "Documento o solicitud inválidos, o Idempotency-Key reutilizada con otra solicitud.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "503", description = "Servicio o serie ocupados (busy): reintente en unos segundos.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "emitSfsDocument")
    @POST
    @Path("/documents/sfs")
    public Response sfs(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid SfsRequestRepresentation request) {
        SfsAdapter.Converted converted = SfsAdapter.convert(request.fileName(), request.cab(), request.det());
        String ruc = QuarkusTransaction.requiringNew().call(() -> {
            access.enter(tenantId, Role.OPERATOR);
            return companyManager.getCompany(companyId).getRuc();
        });
        if (!ruc.equals(converted.ruc())) {
            throw ApiException.invalid("invalid_document", "El RUC del archivo no es el de la empresa.", null);
        }
        EmitManager.Emitted emitted = emit.emitNumbered(tenantId, companyId, idempotencyKey,
                new EmitRequestRepresentation(converted.document(), converted.serie(), null, Map.of("adapter", "sfs")), converted.numero(),
                UUID.randomUUID().toString(), false);
        return Response.status(emitted.status()).entity(emitted.body()).build();
    }

    /** A token for the Nubefact-compatible API (shown once). */
    @APIResponse(responseCode = "201", description = "Token creado; se muestra solo esta vez.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NubefactTokenRepresentation.class)))
    @Operation(operationId = "createNubefactToken")
    @POST
    @Path("/adapter-tokens/nubefact")
    @Transactional
    public Response createToken(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid TokenRequestRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompany(companyId);
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID id = Ids.newId();
        adapterTokens.addNubefactToken(id, tenantId, company.getId(), Tokens.hash(token), request.label(), scope.actor().auditName());
        auditor.record(scope, "adapter_token.created", "company", companyId, Map.of("kind", "nubefact", "label", request.label()));
        return Response.status(201).entity(new NubefactTokenRepresentation(id, token, "/api/v1/public/nubefact/" + company.getId())).build();
    }

    /** The company's adapter tokens (never the token itself). */
    @Operation(operationId = "listAdapterTokens")
    @jakarta.ws.rs.GET
    @Path("/adapter-tokens")
    @Transactional
    public java.util.List<AdapterTokenRepresentation> listTokens(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.ADMIN);
        companyManager.getCompany(companyId);
        return adapterTokens.getAdapterTokens(companyId).stream()
                .map(t -> new AdapterTokenRepresentation(t.id(), t.kind(), t.label(), t.createdBy(), t.createdAt(), t.revokedAt())).toList();
    }


    @APIResponse(responseCode = "204", description = "Token revocado.")
    @Operation(operationId = "revokeAdapterToken")
    @DELETE
    @Path("/adapter-tokens/{tokenId}")
    @Transactional
    public Response revokeToken(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("tokenId") UUID tokenId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        if (!adapterTokens.revokeAdapterToken(tokenId, companyId)) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "adapter_token.revoked", "company", companyId, Map.of("token_id", tokenId.toString()));
        return Response.noContent().build();
    }

}
