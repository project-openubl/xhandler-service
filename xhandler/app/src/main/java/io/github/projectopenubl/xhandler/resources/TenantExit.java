package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.jobs.handlers.TenantExportJob;
import io.github.projectopenubl.xhandler.models.TenantExitProvider;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.models.JobModel;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * A tenant leaving (DESIGN.md §13): a full export first — XML, CDR, PDF and metadata of every
 * document, plus the receptions — in parts of at most {@link Export#PART_DOCUMENTS} documents and
 * an index; then the exit itself, once no document waits for SUNAT and the export covers every
 * document. The tenant becomes {@code exited}: its members can still read it (exports, data-subject
 * requests) but not emit, and its data is deleted 2 years later (the processor's limit, DS
 * 016-2024-JUS art. 31.2) unless it bought the archive plan or a tax audit holds it.
 */
@Path("/api/v1/tenants/{tenantId}")
public class TenantExit {

    private final TenantProvider tenantProvider;

    public record ExitRequest(@NotBlank String confirmName) {}

    /** An export: {@code parts} lists the ZIPs to download (the index first), once {@code done}. */
    public record ExportView(UUID id, String status, List<ExportPart> parts, Integer documents, String detail, String createdAt,
            String doneAt) {}

    /**
     * A part to download by its position in {@code parts}. Its storage key stays internal: it is
     * read only to serve the download.
     */
    public record ExportPart(
            @Schema(required = true, description = "Nombre del ZIP.") String name,
            @Schema(required = true, description = "Tamaño en bytes.") long size,
            @Schema(description = "Comprobantes (o recepciones) en la parte; en el índice, todos los comprobantes.") Integer documents) {}

    static final Duration DELETE_AFTER = Duration.ofDays(730);

    private final TenantAccess access;
    private final TenantExitProvider exits;
    private final Jobs jobs;
    private final FileStore files;
    private final AuditManager auditor;

    public TenantExit(TenantAccess access, TenantExitProvider exits, Jobs jobs, FileStore files, AuditManager auditor, TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
        this.access = access;
        this.exits = exits;
        this.jobs = jobs;
        this.files = files;
        this.auditor = auditor;
    }

    @APIResponse(responseCode = "202", description = "Exportación solicitada; la genera un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExportView.class)))
    @Operation(operationId = "createExport")
    @POST
    @Path("/exports")
    @Transactional
    public Response export(@PathParam("tenantId") UUID tenantId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        if (exits.hasRecentExport()) {
            throw ApiException.conflict("export_recent", "Ya hay una exportación en curso o de la última hora. Use esa.");
        }
        UUID id = Ids.newId();
        exits.addExport(id, tenantId, scope.actor().auditName());
        JobModel job = jobs.enqueue(scope, TenantExportJob.NAME, 4, Map.of("export_id", id.toString()), List.of("export:" + tenantId), null);
        exits.setExportJob(id, job.getId());
        auditor.record(scope, "tenant.export_requested", "tenant", tenantId, Map.of());
        return Response.status(202).entity(view(id)).build();
    }

    @Operation(operationId = "listExports")
    @GET
    @Path("/exports")
    @Transactional
    public List<ExportView> exports(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.ADMIN);
        // An export whose job died (a crashed worker) is failed, not pending forever
        exits.failInterruptedExports();
        List<UUID> ids = exits.getRecentExportIds();
        return ids.stream().map(this::view).toList();
    }

    @APIResponse(responseCode = "200", description = "La parte de la exportación (ZIP).", content = {
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "downloadExportPart")
    @GET
    @Path("/exports/{exportId}/parts/{index}")
    @Transactional
    public Response part(@PathParam("tenantId") UUID tenantId, @PathParam("exportId") UUID exportId, @PathParam("index") int index) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        List<Map<String, Object>> parts = storedParts(exportId);
        if (index < 0 || index >= parts.size()) {
            throw ApiException.notFound();
        }
        Map<String, Object> part = parts.get(index);
        auditor.record(scope, "tenant.export_downloaded", "tenant", tenantId, Map.of("part", String.valueOf(part.get("name"))));
        return Response.ok(files.get((String) part.get("ref"))).type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + part.get("name") + "\"").build();
    }

    /** The tenant after its exit: deleted after {@code deleteAfter}, never when it has an archive plan. */
    public record ExitView(String status, String deleteAfter) {}

    /**
     * Leaves the platform: the organization's name typed, no document waiting for SUNAT, and an
     * export from the last 30 days that is newer than every document.
     */
    @Operation(operationId = "exitTenant")
    @POST
    @Path("/exit")
    @Transactional
    public ExitView exit(@PathParam("tenantId") UUID tenantId, @Valid ExitRequest request) {
        TenantScope scope = access.enter(tenantId, Role.OWNER);
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        if (!"active".equals(tenant.getStatus())) {
            throw ApiException.conflict("not_active", "La organización no está activa.");
        }
        if (!tenant.getName().equals(request.confirmName())) {
            throw ApiException.invalid("invalid_request", "Escriba el nombre exacto de la organización para confirmar.", null);
        }
        long open = exits.countOpenDocuments();
        if (open > 0) {
            throw ApiException.conflict("pending_documents",
                    "Hay " + open + " comprobantes esperando la respuesta de SUNAT. Salga cuando tengan respuesta definitiva.");
        }
        if (!exits.hasExportCoveringAllDocuments()) {
            throw ApiException.conflict("export_required",
                    "Descargue primero una exportación completa, posterior al último comprobante (de los últimos 30 días).");
        }
        Instant deleteAfter = tenant.isArchivePlan() ? null : Instant.now().plus(DELETE_AFTER);
        exits.markExited(tenantId, deleteAfter);
        auditor.record(scope, "tenant.exit", "tenant", tenantId, Map.of("delete_after", String.valueOf(deleteAfter)));
        return new ExitView("exited", deleteAfter == null ? null : deleteAfter.toString());
    }

    ExportView view(UUID id) {
        TenantExitProvider.ExportRow r = exits.getExport(id).orElseThrow(ApiException::notFound);
        List<ExportPart> parts = parse(r.parts()).stream()
                .map(p -> new ExportPart(String.valueOf(p.get("name")), ((Number) p.get("size")).longValue(),
                        p.get("documents") == null ? null : ((Number) p.get("documents")).intValue()))
                .toList();
        return new ExportView(r.id(), r.status(), parts, r.documents(), r.detail(), String.valueOf(r.createdAt()),
                r.doneAt() == null ? null : String.valueOf(r.doneAt()));
    }

    /** The parts as stored, with their storage keys ({@code ref}): only for downloads, never returned. */
    private List<Map<String, Object>> storedParts(UUID id) {
        return parse(exits.getExportParts(id).orElseThrow(ApiException::notFound).parts());
    }

    private static List<Map<String, Object>> parse(String parts) {
        if (parts == null) {
            return List.of();
        }
        try {
            return new ObjectMapper().readValue(parts, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }


}
