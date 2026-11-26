package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.representations.idm.Page;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.resources.CompanyResource;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.resources.StatusResource;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.models.ConformityProvider;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Factura negociable conformity (DESIGN.md §11.8): per document, per company, and an export for
 * factoring (no CAVALI integration). An objected factura gets a guided credit note type 13.
 */
@Path("/api/v1/tenants/{tenantId}")
public class ConformityResource {

    private final CompanyManager companyManager;

    private final DocumentProvider documentProvider;

    public record ConformityView(
            UUID documentId, String serieNumero, String buyerRuc, String state, Instant acceptedAt, Instant windowEndsAt, Instant checkedAt,
            String checkDetail, Instant resolvedAt, Map<String, Object> suggestedCreditNote) {}

    private final TenantAccess access;
    private final ConformityProvider conformityProvider;
    private final Auditor auditor;

    public ConformityResource(TenantAccess access, ConformityProvider conformityProvider, Auditor auditor, DocumentProvider documentProvider, CompanyManager companyManager) {
        this.companyManager = companyManager;
        this.documentProvider = documentProvider;
        this.access = access;
        this.conformityProvider = conformityProvider;
        this.auditor = auditor;
    }

    @Operation(operationId = "getDocumentConformity")
    @GET
    @Path("/documents/{documentId}/conformity")
    @Transactional
    public ConformityView document(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        access.enter(tenantId, Role.VIEWER);
        ConformityProvider.ConformityRow r = conformityProvider.getDocumentConformity(documentId).orElseThrow(ApiException::notFound);
        Map<String, Object> creditNote = null;
        if ("objected".equals(r.state())) {
            creditNote = creditNote13(documentProvider.getDocument(documentId).orElse(null));
        }
        return view(r, creditNote);
    }

    @Operation(operationId = "listConformity", summary = "Facturas negociables de la empresa y su conformidad, las más recientes primero")
    @GET
    @Path("/companies/{companyId}/conformity")
    @Transactional
    public Page<ConformityView> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Schema(description = "pending, agreed, objected o silent_agreement; sin filtro, todas.") @QueryParam("state") String state,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        companyManager.getCompany(companyId);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        return new Page<>(rows(companyId, state, size, start).stream().map(r -> view(r, null)).toList(), size, start);
    }

    /** For factoring: facturas negociables and their conformity, as CSV. */
    @APIResponse(responseCode = "200", description = "Facturas negociables y su conformidad (CSV).", content = {
            @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "exportConformity")
    @GET
    @Path("/companies/{companyId}/conformity/export")
    @Transactional
    public Response export(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @QueryParam("state") String state) {
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        companyManager.getCompany(companyId);
        StringBuilder csv = new StringBuilder("﻿serie_numero,fecha_emision,adquirente_ruc,adquirente,moneda,total,conformidad,"
                + "aceptada_sunat,vence_plazo,resuelta\r\n");
        for (ConformityProvider.ConformityRow r : rows(companyId, state, EXPORT_LIMIT, 0)) {
            csv.append(String.join(",", List.of(r.serie() + "-" + r.numero(), String.valueOf(r.fechaEmision()), nz(r.buyerRuc()),
                    quote(nz(r.customerNombre())), nz(r.moneda()), nz(r.total()), r.state(), String.valueOf(r.acceptedAt()),
                    String.valueOf(r.windowEndsAt()), r.resolvedAt() == null ? "" : String.valueOf(r.resolvedAt())))).append("\r\n");
        }
        auditor.record(scope, "conformity.exported", "company", companyId, Map.of("state", state == null ? "all" : state));
        return Response.ok(csv.toString().getBytes(StandardCharsets.UTF_8)).type("text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"facturas-negociables-" + companyId + ".csv\"").build();
    }

    /** Rows in one CSV export: the most recent ones. */
    private static final int EXPORT_LIMIT = 1000;

    private List<ConformityProvider.ConformityRow> rows(UUID companyId, String state, int limit, int offset) {
        if (state != null && !List.of("pending", "agreed", "objected", "silent_agreement").contains(state)) {
            throw ApiException.invalid("invalid_request", "state debe ser pending, agreed, objected o silent_agreement.", null);
        }
        return conformityProvider.getConformities(companyId, state, limit, offset);
    }

    /**
     * The guided correction after an objection: a credit note type 13 (ajustes de montos y/o fechas
     * de pago) on the factura, to complete with the corrected cuotas and send to {@code POST /documents}.
     */
    static Map<String, Object> creditNote13(DocumentModel d) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("tipo_documento_identidad", d.getCustomerTipo());
        cliente.put("numero_documento_identidad", d.getCustomerNumero());
        cliente.put("nombre", d.getCustomerNombre());
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("tipo_nota", "13");
        spec.put("comprobante_afectado_tipo", "01");
        spec.put("comprobante_afectado_serie_numero", d.serieNumero());
        spec.put("descripcion_sustento", "Ajuste del monto neto pendiente de pago y/o de las fechas de vencimiento");
        spec.put("moneda", d.getMoneda());
        spec.put("cliente", cliente);
        spec.put("forma_pago", Map.of("tipo", "Credito", "cuotas", List.of()));
        spec.put("detalles", List.of());
        return Map.of("kind", "CreditNote", "spec", spec);
    }

    private static ConformityView view(ConformityProvider.ConformityRow r, Map<String, Object> creditNote) {
        return new ConformityView(r.documentId(), r.serie() + "-" + r.numero(), r.buyerRuc(), r.state(), r.acceptedAt(),
                r.windowEndsAt(), r.checkedAt(), r.checkDetail(), r.resolvedAt(), creditNote);
    }

    private static String nz(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String quote(String value) {
        String v = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
