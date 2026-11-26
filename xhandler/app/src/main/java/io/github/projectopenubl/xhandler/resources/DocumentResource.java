package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.VoidRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.EmitRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.DocumentEventRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.AccessLinkRepresentation;
import io.github.projectopenubl.xhandler.utils.DocumentFiles;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.DocumentSearch;
import io.github.projectopenubl.xhandler.models.DocumentFileProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.managers.AccessLinkManager;
import io.github.projectopenubl.xhandler.managers.DocumentControlManager;
import io.github.projectopenubl.xhandler.managers.EmitManager;
import io.github.projectopenubl.xhandler.managers.VoidingManager;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.DocumentEventModel;
import io.github.projectopenubl.xhandler.representations.idm.DocumentRepresentation;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Issuing and tracking documents (DESIGN.md §11, §12). */
@Path("/api/v1/tenants/{tenantId}")
public class DocumentResource {

    private final ModelToRepresentation modelToRepresentation;

    private final DocumentProvider documentProvider;

    private final DocumentFileProvider documentFileProvider;

    private final DocumentEventProvider documentEventProvider;



    private final TenantAccess access;
    private final VoidingManager voiding;
    private final EmitManager emit;
    private final DocumentControlManager control;
    private final AccessLinkManager links;
    private final FileStore files;
    private final AuditManager auditor;
    private final io.github.projectopenubl.xhandler.pdf.DocumentPdf pdf;

    public DocumentResource(TenantAccess access, EmitManager emit, DocumentControlManager control, AccessLinkManager links, FileStore files,
            AuditManager auditor, VoidingManager voiding, io.github.projectopenubl.xhandler.pdf.DocumentPdf pdf, DocumentEventProvider documentEventProvider, DocumentFileProvider documentFileProvider, DocumentProvider documentProvider, ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.documentProvider = documentProvider;
        this.documentFileProvider = documentFileProvider;
        this.documentEventProvider = documentEventProvider;
        this.pdf = pdf;
        this.access = access;
        this.voiding = voiding;
        this.emit = emit;
        this.control = control;
        this.links = links;
        this.files = files;
        this.auditor = auditor;
    }

    @POST
    @Path("/companies/{companyId}/documents")
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
    @Operation(operationId = "emitDocument", summary = "Emite un documento: lo numera, valida y firma; SUNAT lo recibe después")
    public Response create(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @HeaderParam("X-Correlation-Id") String correlationId,
            EmitRequestRepresentation request) {
        EmitManager.Emitted emitted = emit.emit(tenantId, companyId, idempotencyKey, request, Ids.correlation(correlationId));
        return Response.status(emitted.status()).entity(emitted.body()).build();
    }

    @GET
    @Path("/documents")
    @Transactional
    @Operation(operationId = "listDocuments")
    public PageRepresentation<DocumentRepresentation> list(
            @PathParam("tenantId") UUID tenantId,
            @QueryParam("company_id") UUID companyId,
            @QueryParam("status") String status,
            @QueryParam("tipo_comprobante") String tipo,
            @QueryParam("serie") String serie,
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("hold_reason") String holdReason,
            @QueryParam("label") List<String> labels,
            @Schema(description = "Número de documento del comprador (RUC, DNI, …), exacto.") @QueryParam("customer_numero") String customerNumero,
            @Schema(description = "Inicio del nombre o razón social del comprador, sin distinguir mayúsculas.") @QueryParam("customer_nombre")
                    String customerNombre,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        Map<String, String> labelFilters = new java.util.LinkedHashMap<>();
        for (String label : labels == null ? List.<String>of() : labels) {
            int colon = label.indexOf(':');
            if (colon <= 0) {
                throw ApiException.invalid("invalid_request", "Filtre etiquetas como label=clave:valor.", null);
            }
            labelFilters.put(label.substring(0, colon), label.substring(colon + 1));
        }
        DocumentSearch search = new DocumentSearch(companyId, status, tipo, serie, holdReason,
                customerNumero == null || customerNumero.isBlank() ? null : customerNumero.strip(),
                customerNombre == null || customerNombre.isBlank() ? null : likePrefix(customerNombre.strip().toLowerCase(java.util.Locale.ROOT)),
                from, to, labelFilters);
        int size = PageRepresentation.limit(limit);
        int start = PageRepresentation.offset(offset);
        List<DocumentModel> page = documentProvider.searchDocuments(search, start, size);
        return new PageRepresentation<>(page.stream().map(modelToRepresentation::toRepresentation).toList(), size, start);
    }

    @GET
    @Path("/documents/{documentId}")
    @Transactional
    @Operation(operationId = "getDocument")
    public DocumentRepresentation get(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        access.enter(tenantId, Role.VIEWER);
        return modelToRepresentation.toRepresentation(find(documentId));
    }

    @GET
    @Path("/documents/{documentId}/events")
    @Transactional
    @Operation(operationId = "listDocumentEvents", summary = "Línea de tiempo del documento")
    public List<DocumentEventRepresentation> events(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        access.enter(tenantId, Role.VIEWER);
        find(documentId);
        List<DocumentEventModel> events = documentEventProvider.getDocumentEvents(documentId);
        return events.stream().map(e -> new DocumentEventRepresentation(e.getAt(), e.getStatus(), e.getCode(), e.getMessage(), e.getActor(), e.getCorrelationId())).toList();
    }

    /** XML, CDR, input or enriched JSON. Reading a document's files is audited (personal data, §3.4). */
    @GET
    @Path("/documents/{documentId}/files/{kind}")
    @Transactional
    @APIResponse(responseCode = "200", description = "El archivo: XML firmado, CDR (ZIP), PDF, o el JSON de entrada o enriquecido.", content = {
            @Content(mediaType = "application/xml", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/pdf", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/json", schema = @Schema(implementation = com.fasterxml.jackson.databind.JsonNode.class))
    })
    @Operation(operationId = "downloadDocumentFile")
    public Response file(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId,
            @PathParam("kind") String kind) {
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        DocumentModel doc = find(documentId);
        String stored = switch (kind) {
            case "xml" -> "xml_signed";
            case "cdr" -> "cdr_zip";
            case "input", "enriched", "pdf" -> kind;
            default -> throw ApiException.notFound();
        };
        byte[] content = "pdf".equals(kind)
                ? pdf.render(doc.getId(), null)
                : files.get(documentFileProvider.getDocumentFile(doc.getId(), stored).orElseThrow(ApiException::notFound).getStorageKey());
        auditor.record(scope, "document.file_read", "document", doc.getId(), Map.of("kind", kind));
        return Response.ok(content)
                .type(DocumentFiles.contentType(kind))
                .header("Content-Disposition", "attachment; filename=\"" + DocumentFiles.fileName(scope, doc, kind) + "\"")
                .build();
    }

    @POST
    @Path("/documents/{documentId}/retry")
    @Transactional
    @Operation(operationId = "retryDocument", summary = "Reintenta el envío ahora (exception: reenvía con el mismo número)")
    public DocumentRepresentation retry(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        return modelToRepresentation.toRepresentation(control.retry(access.enter(tenantId, Role.OPERATOR), documentId));
    }

    @POST
    @Path("/documents/{documentId}/hold")
    @Transactional
    @Operation(operationId = "holdDocument")
    public DocumentRepresentation hold(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        return modelToRepresentation.toRepresentation(control.hold(access.enter(tenantId, Role.OPERATOR), documentId));
    }

    @POST
    @Path("/documents/{documentId}/release")
    @Transactional
    @Operation(operationId = "releaseDocument")
    public DocumentRepresentation release(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        return modelToRepresentation.toRepresentation(control.release(access.enter(tenantId, Role.OPERATOR), documentId));
    }

    /**
     * Voids an accepted document the buyer doesn't have (§9.3, §11.3): RA, RC estado 3 or RR. 202:
     * SUNAT answers later; the document is {@code voiding} until then.
     */
    @POST
    @Path("/documents/{documentId}/void")
    @APIResponse(responseCode = "202", description = "Anulación enviada: el documento queda en voiding hasta la respuesta de SUNAT.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DocumentRepresentation.class)))
    @Operation(operationId = "voidDocument", summary = "Anula un documento aceptado y no entregado (RA, RC o RR)")
    public Response voidDocument(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId,
            @HeaderParam("X-Correlation-Id") String correlationId, VoidRequestRepresentation request) {
        DocumentModel voidingDocument = voiding.voidDocument(tenantId, documentId, request, Ids.correlation(correlationId));
        return Response.status(202).entity(modelToRepresentation.toRepresentation(voidingDocument)).build();
    }

    /** Cancels a void whose RA/RC/RR ended in exception: the document goes back to its previous status. */
    @POST
    @Path("/documents/{documentId}/void/cancel")
    @Operation(operationId = "cancelVoid")
    public DocumentRepresentation cancelVoid(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        return modelToRepresentation.toRepresentation(voiding.cancelVoid(tenantId, documentId));
    }

    /** The client attests the buyer received the document (§9.3): it can no longer be voided. */
    @POST
    @Path("/documents/{documentId}/delivered")
    @Transactional
    @Operation(operationId = "markDocumentDelivered")
    public DocumentRepresentation delivered(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        DocumentModel doc = control.lock(documentId);
        if (doc.getDeliveredAt() == null) {
            doc.setDeliveredAt(Instant.now());
            doc.setDeliveredSource("client");
            doc.setUpdatedAt(doc.getDeliveredAt());
            documentEventProvider.addDocumentEvent(doc, null, "delivered (client)", scope.actor().auditName(), null);
            auditor.record(scope, "document.delivered", "document", doc.getId());
        }
        return modelToRepresentation.toRepresentation(doc);
    }

    /** A new buyer link (the previous ones stay valid until they expire). */
    @POST
    @Path("/documents/{documentId}/access-link")
    @Transactional
    @Operation(operationId = "createDocumentAccessLink")
    public AccessLinkRepresentation accessLink(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        DocumentModel doc = find(documentId);
        auditor.record(scope, "document.access_link_created", "document", doc.getId());
        return new AccessLinkRepresentation(links.url(links.create(doc)));
    }

    /** Ends every buyer link of the document now (e.g. sent to the wrong person). */
    @POST
    @Path("/documents/{documentId}/access-links/revoke")
    @Transactional
    @APIResponse(responseCode = "204", description = "Enlaces revocados.")
    @Operation(operationId = "revokeDocumentAccessLinks")
    public Response revokeAccessLinks(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        DocumentModel doc = find(documentId);
        int revoked = documentProvider.revokeAccessLinks(doc.getId());
        auditor.record(scope, "document.access_links_revoked", "document", doc.getId(), Map.of("count", revoked));
        return Response.noContent().build();
    }


    DocumentModel find(UUID documentId) {
        DocumentModel doc = documentProvider.getDocument(documentId).orElse(null);
        if (doc == null) {
            throw ApiException.notFound();
        }
        return doc;
    }



    private static void add(StringBuilder query, Map<String, Object> params, String field, Object value) {
        if (value != null) {
            query.append(" and ").append(field).append(" = :").append(field);
            params.put(field, value);
        }
    }


    /** A LIKE pattern matching values that start with {@code text}, its wildcards escaped. */
    static String likePrefix(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
