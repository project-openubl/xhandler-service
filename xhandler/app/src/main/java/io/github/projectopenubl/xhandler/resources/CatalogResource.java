package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.EntryRepresentation;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Constraints;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.models.CatalogProvider;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Optional catalogs of a company (DESIGN.md §17): customers, products, vehicles, drivers and
 * addresses. Documents may reference them or carry everything inline. One table typed by kind; each
 * kind has its required fields and natural key (a customer by its document, a product by its code,
 * a vehicle by its plate). Soft-deleted, sync-ready (§7.2).
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/catalogs/{kind}")
public class CatalogResource {

    private final CompanyManager companyManager;


    /** Required fields and the fields that make the key, per kind. */
    static final Map<String, List<String>> REQUIRED = Map.of(
            "customer", List.of("tipo_documento_identidad", "numero_documento_identidad", "nombre"),
            "product", List.of("codigo", "descripcion"),
            "vehicle", List.of("placa"),
            "driver", List.of("tipo_documento_identidad", "numero_documento_identidad", "nombre", "licencia"),
            "address", List.of("direccion"));

    static final Map<String, List<String>> KEY = Map.of(
            "customer", List.of("tipo_documento_identidad", "numero_documento_identidad"),
            "product", List.of("codigo"),
            "vehicle", List.of("placa"),
            "driver", List.of("tipo_documento_identidad", "numero_documento_identidad"),
            "address", List.of());

    private final TenantAccess access;
    private final CatalogProvider catalogProvider;
    private final ObjectMapper json;
    private final io.github.projectopenubl.xhandler.managers.AuditManager auditor;

    public CatalogResource(TenantAccess access, CatalogProvider catalogProvider, ObjectMapper json, io.github.projectopenubl.xhandler.managers.AuditManager auditor, CompanyManager companyManager) {
        this.companyManager = companyManager;
        this.auditor = auditor;
        this.access = access;
        this.catalogProvider = catalogProvider;
        this.json = json;
    }

    @Operation(operationId = "listCatalogEntries")
    @GET
    @Transactional
    public PageRepresentation<EntryRepresentation> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @QueryParam("q") String q, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        var scope = access.enter(tenantId, Role.VIEWER);
        check(kind);
        companyManager.getCompany(companyId);
        auditPersonalData(scope, kind, companyId, "list");
        int size = PageRepresentation.limit(limit);
        int from = PageRepresentation.offset(offset);
        List<CatalogProvider.CatalogRow> rows = catalogProvider.searchEntries(companyId, kind, q, size, from);
        return new PageRepresentation<>(rows.stream().map(this::entry).toList(), size, from);
    }

    @Operation(operationId = "getCatalogEntry")
    @GET
    @Path("/{id}")
    @Transactional
    public EntryRepresentation get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @PathParam("id") UUID id) {
        var scope = access.enter(tenantId, Role.VIEWER);
        check(kind);
        EntryRepresentation entry = find(companyId, kind, id);
        auditPersonalData(scope, kind, companyId, id.toString());
        return entry;
    }

    @APIResponse(responseCode = "201", description = "Registro creado o actualizado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EntryRepresentation.class)))
    @Operation(operationId = "createCatalogEntry")
    @POST
    @Transactional
    public Response create(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            ObjectNode data) {
        access.enter(tenantId, Role.OPERATOR);
        check(kind);
        var company = companyManager.getCompany(companyId);
        return Response.status(201).entity(upsert(company.getTenantId(), companyId, kind, data, null)).build();
    }

    @Operation(operationId = "updateCatalogEntry")
    @PUT
    @Path("/{id}")
    @Transactional
    public EntryRepresentation update(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @PathParam("id") UUID id, ObjectNode data) {
        access.enter(tenantId, Role.OPERATOR);
        check(kind);
        find(companyId, kind, id);
        return upsert(tenantId, companyId, kind, data, id);
    }

    @APIResponse(responseCode = "204", description = "Registro eliminado.")
    @Operation(operationId = "deleteCatalogEntry")
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("kind") String kind,
            @PathParam("id") UUID id) {
        access.enter(tenantId, Role.OPERATOR);
        check(kind);
        find(companyId, kind, id);
        catalogProvider.deleteEntry(id);
        return Response.noContent().build();
    }

    /** Creates or updates an entry, with the tenant open. Used by the API and the RUC/DNI lookup cache. */
    @Transactional(Transactional.TxType.MANDATORY)
    public EntryRepresentation upsert(UUID tenantId, UUID companyId, String kind, ObjectNode data, UUID id) {
        if (data == null) {
            throw ApiException.invalid("invalid_request", "Envíe los datos del registro.", null);
        }
        for (String field : REQUIRED.get(kind)) {
            if (!data.hasNonNull(field) || data.get(field).asText().isBlank()) {
                throw ApiException.invalid("invalid_request", "Falta el campo " + field + ".", null);
            }
        }
        String key = KEY.get(kind).isEmpty()
                ? (id != null ? id.toString() : Ids.newId().toString())
                : String.join(":", KEY.get(kind).stream().map(f -> data.get(f).asText().strip().toUpperCase(java.util.Locale.ROOT)).toList());
        UUID entryId = id != null ? id : Ids.newId();
        try {
            if (id == null) {
                catalogProvider.addEntry(entryId, tenantId, companyId, kind, key, data.toString(),
                        data.hasNonNull("device_id") ? data.get("device_id").asText() : null);
            } else {
                catalogProvider.updateEntry(id, key, data.toString());
            }
            catalogProvider.flush();
        } catch (PersistenceException e) {
            if (Constraints.violated(e).isPresent()) {
                throw ApiException.conflict("catalog_entry_exists", "Ya existe un registro con esa clave: " + key + ".");
            }
            throw e;
        }
        return find(companyId, kind, entryId);
    }

    /** An entry by its natural key, if present (with the tenant open). */
    @Transactional(Transactional.TxType.MANDATORY)
    public java.util.Optional<EntryRepresentation> byKey(UUID companyId, String kind, String key) {
        return catalogProvider.getEntryByKey(companyId, kind, key).map(this::entry);
    }

    private EntryRepresentation find(UUID companyId, String kind, UUID id) {
        return catalogProvider.getEntry(companyId, kind, id).map(this::entry).orElseThrow(ApiException::notFound);
    }

    private EntryRepresentation entry(CatalogProvider.CatalogRow r) {
        try {
            return new EntryRepresentation(r.id(), r.kind(), r.key(), json.readTree(r.data()), r.createdAt(), r.updatedAt());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Customers and drivers are people's data: reads are audited (§3.4). */
    private void auditPersonalData(io.github.projectopenubl.xhandler.security.TenantScope scope, String kind, UUID companyId, String what) {
        if ("customer".equals(kind) || "driver".equals(kind)) {
            auditor.record(scope, "catalog.read", kind, companyId, Map.of("entry", what));
        }
    }

    private static void check(String kind) {
        if (!REQUIRED.containsKey(kind)) {
            throw ApiException.notFound();
        }
    }
}
