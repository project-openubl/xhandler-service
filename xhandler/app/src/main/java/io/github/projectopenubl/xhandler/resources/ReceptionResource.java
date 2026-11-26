package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.ReceptionRepresentation;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.jobs.handlers.ReceptionValidity;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.github.projectopenubl.xhandler.models.ReceptionProvider;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Supplier invoices received by a company (DESIGN.md §11.5): XML plus optional PDF and CDR, all
 * untrusted (size caps; the engine refuses DTDs). The document must be addressed to the company.
 * Its validity is checked with SUNAT, now if possible and again by a job while unknown. Kept for
 * the purchases side of SIRE.
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/receptions")
public class ReceptionResource {

    private final CompanyManager companyManager;


    static final int MAX_XML = 5 * 1024 * 1024;
    static final int MAX_ATTACHMENT = 10 * 1024 * 1024;

    private final TenantAccess access;
    private final Engine engine;
    private final FileStore files;
    private final ReceptionProvider receptionProvider;
    private final AuditManager auditor;
    private final ReceptionValidity validity;

    public ReceptionResource(TenantAccess access, Engine engine, FileStore files, ReceptionProvider receptionProvider, AuditManager auditor, ReceptionValidity validity, CompanyManager companyManager) {
        this.companyManager = companyManager;
        this.access = access;
        this.engine = engine;
        this.files = files;
        this.receptionProvider = receptionProvider;
        this.auditor = auditor;
        this.validity = validity;
    }

    @APIResponse(responseCode = "201", description = "Comprobante recibido.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReceptionRepresentation.class)))
    @Operation(operationId = "receiveDocument")
    @POST
    public Response receive(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @RestForm("xml") FileUpload xml,
            @RestForm("pdf") FileUpload pdf, @RestForm("cdr") FileUpload cdr) throws IOException {
        if (xml == null) {
            throw ApiException.invalid("invalid_request", "Envíe el XML del comprobante (campo xml).", null);
        }
        // Authorize before reading uploads or making the engine parse them
        QuarkusTransaction.requiringNew().run(() -> {
            access.enter(tenantId, Role.OPERATOR);
            companyManager.getCompany(companyId);
        });
        byte[] xmlBytes = read(xml, MAX_XML, "El XML");
        byte[] pdfBytes = pdf == null ? null : read(pdf, MAX_ATTACHMENT, "El PDF");
        byte[] cdrBytes = cdr == null ? null : read(cdr, MAX_ATTACHMENT, "El CDR");
        EngineModels.InspectResponse identity = engine.inspect(io.github.projectopenubl.xhandler.utils.XmlText.decode(xmlBytes));
        if (identity.tipoComprobante() == null || identity.numero() == null) {
            throw ApiException.invalid("invalid_document", "El XML no es un comprobante.", null);
        }
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.OPERATOR);
            CompanyModel company = companyManager.getCompany(companyId);
            if (!"6".equals(identity.customerTipo()) || !company.getRuc().equals(identity.customerNumero())) {
                throw ApiException.invalid("invalid_document", "El comprobante no está emitido a la empresa (RUC del adquirente).", null);
            }
            UUID receptionId = Ids.newId();
            try {
                String amounts = amountsJson(identity);
                String xmlRef = files.put(tenantId, xmlBytes).key();
                String pdfRef = pdfBytes == null ? null : files.put(tenantId, pdfBytes).key();
                String cdrRef = cdrBytes == null ? null : files.put(tenantId, cdrBytes).key();
                receptionProvider.addReception(new ReceptionProvider.NewReception(receptionId, tenantId, companyId, identity.ruc(),
                        identity.tipoComprobante(), identity.serie(), identity.numero(),
                        identity.fechaEmision() == null ? null : LocalDate.parse(identity.fechaEmision()), identity.moneda(),
                        identity.total() == null ? null : new BigDecimal(identity.total()), identity.digestValue(), xmlRef, pdfRef, cdrRef,
                        amounts, identity.affectedSerieNumero(), identity.affectedTipo()));
            } catch (PersistenceException e) {
                if (io.github.projectopenubl.xhandler.utils.Constraints.violated(e).isPresent()) {
                    throw ApiException.conflict("reception_exists", "Ese comprobante ya fue recibido.");
                }
                throw e;
            }
            auditor.record(scope, "reception.received", "reception", receptionId,
                    Map.of("supplier_ruc", identity.ruc(), "serie_numero", identity.serie() + "-" + identity.numero()));
            return receptionId;
        });
        try {
            validity.check(tenantId, id);
        } catch (RuntimeException e) {
            // The reception is stored; the validity job checks it later
            org.jboss.logging.Logger.getLogger(ReceptionResource.class).warnf("validity check of reception %s failed: %s", id, e.toString());
        }
        return Response.status(201).entity(QuarkusTransaction.requiringNew().call(() -> {
            access.enter(tenantId, Role.OPERATOR);
            return find(id);
        })).build();
    }

    @Operation(operationId = "listReceptions")
    @GET
    @Transactional
    public PageRepresentation<ReceptionRepresentation> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to, @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        companyManager.getCompany(companyId);
        int size = PageRepresentation.limit(limit);
        int start = PageRepresentation.offset(offset);
        List<UUID> ids = receptionProvider.getReceptionIds(companyId, from, to, size, start);
        return new PageRepresentation<>(ids.stream().map(this::find).toList(), size, start);
    }

    @Operation(operationId = "getReception")
    @GET
    @Path("/{receptionId}")
    @Transactional
    public ReceptionRepresentation get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("receptionId") UUID receptionId) {
        access.enter(tenantId, Role.VIEWER);
        companyManager.getCompany(companyId);
        if (!receptionProvider.existsReception(receptionId, companyId)) {
            throw ApiException.notFound();
        }
        return find(receptionId);
    }

    @APIResponse(responseCode = "200", description = "El archivo: XML, PDF o CDR (ZIP).", content = {
            @Content(mediaType = "application/xml", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/pdf", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "downloadReceptionFile")
    @GET
    @Path("/{receptionId}/files/{kind}")
    @Transactional
    public Response file(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("receptionId") UUID receptionId,
            @PathParam("kind") String kind) {
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        if (!List.of("xml", "pdf", "cdr").contains(kind)) {
            throw ApiException.notFound();
        }
        String ref = receptionProvider.getFileRef(receptionId, companyId, kind).orElseThrow(ApiException::notFound);
        auditor.record(scope, "reception.file_read", "reception", receptionId, Map.of("kind", kind));
        // Supplier files are untrusted: downloaded, never rendered from the API origin
        return Response.ok(files.get(ref)).type("xml".equals(kind) ? "application/xml" : "pdf".equals(kind) ? "application/pdf"
                : "application/zip").header("Content-Disposition", "attachment; filename=\"" + receptionId + "." + ("cdr".equals(kind) ? "zip" : kind) + "\"")
                .header("X-Content-Type-Options", "nosniff").build();
    }

    private String amountsJson(EngineModels.InspectResponse identity) {
        try {
            var amounts = DocumentModel.amountsOf(identity.amounts());
            return amounts == null ? null : JSON.writeValueAsString(amounts);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private ReceptionRepresentation find(UUID id) {
        ReceptionProvider.ReceptionRow r = receptionProvider.getReception(id).orElseThrow(ApiException::notFound);
        return new ReceptionRepresentation(r.id(), r.supplierRuc(), r.tipoComprobante(), r.serieNumero(), r.fechaEmision(), r.moneda(), r.total(),
                r.validityState(), r.validityCheckedAt(), r.hasPdf(), r.hasCdr(), r.createdAt());
    }

    private static byte[] read(FileUpload upload, int max, String what) throws IOException {
        if (upload.size() > max) {
            throw ApiException.invalid("invalid_request", what + " es demasiado grande.", null);
        }
        return Files.readAllBytes(upload.uploadedFile());
    }
}
