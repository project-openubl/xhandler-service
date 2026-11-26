package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.TenantExitProvider;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.models.JobModel;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.pdf.DocumentPdf;
import io.github.projectopenubl.xhandler.resources.StatusResource;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

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
    private final Auditor auditor;

    public TenantExit(TenantAccess access, TenantExitProvider exits, Jobs jobs, FileStore files, Auditor auditor, TenantProvider tenantProvider) {
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
        JobModel job = jobs.enqueue(scope, Export.NAME, 4, Map.of("export_id", id.toString()), List.of("export:" + tenantId), null);
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

    /**
     * Builds the export page by page: parts of at most {@link #PART_DOCUMENTS} documents (XML, CDR,
     * PDF and JSON each, named {@code env/RUC-TT-SERIE-NUMERO} like SUNAT), parts of receptions, and
     * an index. Memory stays bounded by one part.
     */
    @ApplicationScoped
    public static class Export implements JobHandler {
        public static final String NAME = "tenants.export";
        static final int PART_DOCUMENTS = 300;
        private static final ObjectMapper JSON = new ObjectMapper();

        private final TenantAccess access;
        private final TenantExitProvider exits;
        private final FileStore files;
        private final DocumentPdf pdf;

        public Export(TenantAccess access, TenantExitProvider exits, FileStore files, DocumentPdf pdf) {
            this.access = access;
            this.exits = exits;
            this.files = files;
            this.pdf = pdf;
        }

        @Override
        public String name() {
            return NAME;
        }

        record Item(UUID id, String name, String xmlRef, String cdrRef, Map<String, Object> meta) {}

        @Override
        public Map<String, Object> run(JobContext context) {
            UUID exportId = context.uuidArg("export_id");
            UUID tenantId = context.tenantId();
            try {
                List<Map<String, Object>> parts = new ArrayList<>();
                List<Map<String, Object>> index = new ArrayList<>();
                Set<String> names = new HashSet<>();
                int documents = 0;
                // Keyset pagination: (created_at, id) of the last document of the previous page
                Object[] after = {Instant.EPOCH, new UUID(0, 0)};
                while (true) {
                    List<Item> page = documents(tenantId, (Instant) after[0], (UUID) after[1]);
                    if (page.isEmpty()) {
                        break;
                    }
                    Map<String, byte[]> entries = new LinkedHashMap<>();
                    for (Item item : page) {
                        if (!names.add(item.name())) {
                            throw new IllegalStateException("two documents named " + item.name());
                        }
                        if (item.xmlRef() != null) {
                            entries.put(item.name() + ".xml", files.get(item.xmlRef()));
                        }
                        if (item.cdrRef() != null) {
                            entries.put("R-" + item.name() + ".zip", files.get(item.cdrRef()));
                        }
                        try {
                            byte[] rendered = QuarkusTransaction.requiringNew().call(() -> {
                                access.enterAsPlatform(tenantId);
                                return pdf.render(item.id(), "");
                            });
                            entries.put(item.name() + ".pdf", rendered);
                        } catch (RuntimeException e) {
                            item.meta().put("pdf", "no disponible");
                        }
                        entries.put(item.name() + ".json", JSON.writeValueAsBytes(item.meta()));
                        index.add(item.meta());
                    }
                    documents += page.size();
                    FileStore.Stored stored = files.put(tenantId, zip(entries));
                    parts.add(Map.of("name", String.format("documentos-%04d.zip", parts.size() + 1), "ref", stored.key(), "size", stored.size(),
                            "documents", page.size()));
                    Item last = page.get(page.size() - 1);
                    after = new Object[] {Instant.parse((String) last.meta().get("created_at")), last.id()};
                }
                parts.addAll(receptions(tenantId, index));
                FileStore.Stored indexStored = files.put(tenantId, zip(Map.of("indice.json", JSON.writeValueAsBytes(index))));
                parts.add(0, Map.of("name", "indice.zip", "ref", indexStored.key(), "size", indexStored.size(), "documents", documents));
                String partsJson = JSON.writeValueAsString(parts);
                int total = documents;
                QuarkusTransaction.requiringNew().run(() -> {
                    access.enterAsPlatform(tenantId);
                    exits.markExportDone(exportId, partsJson, total);
                });
                return Map.of("documents", documents, "parts", parts.size());
            } catch (Exception e) {
                Logger.getLogger(Export.class).errorf(e, "export %s failed", exportId);
                QuarkusTransaction.requiringNew().run(() -> {
                    access.enterAsPlatform(tenantId);
                    exits.markExportFailed(exportId, "No se pudo generar la exportación.");
                });
                return Map.of("status", "failed");
            }
        }

        private List<Item> documents(UUID tenantId, Instant afterCreated, UUID afterId) {
            return QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(tenantId);
                List<Item> out = new ArrayList<>();
                for (TenantExitProvider.ExportDocumentRow r : exits.getExportDocuments(afterCreated, afterId, PART_DOCUMENTS)) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("id", r.id().toString());
                    meta.put("ruc", r.ruc());
                    meta.put("sunat_env", r.sunatEnv());
                    meta.put("tipo_comprobante", r.tipoComprobante());
                    meta.put("serie_numero", r.serie() + "-" + r.numero());
                    meta.put("fecha_emision", String.valueOf(r.fechaEmision()));
                    meta.put("status", r.status());
                    meta.put("sunat_code", r.sunatCode());
                    meta.put("moneda", r.moneda());
                    meta.put("total", r.total() == null ? null : r.total().toString());
                    meta.put("customer_tipo", r.customerTipo());
                    meta.put("customer", r.customerNumero());
                    meta.put("created_at", String.valueOf(r.createdAt()));
                    String name = r.sunatEnv() + "/" + r.ruc() + "-" + r.tipoComprobante() + "-" + r.serie() + "-" + r.numero();
                    out.add(new Item(r.id(), name, r.xmlRef(), r.cdrRef(), meta));
                }
                return out;
            });
        }

        /** Supplier documents received, in parts too (the purchases side of the records). */
        private List<Map<String, Object>> receptions(UUID tenantId, List<Map<String, Object>> index) throws IOException {
            List<Map<String, Object>> parts = new ArrayList<>();
            UUID after = new UUID(0, 0);
            while (true) {
                UUID from = after;
                List<TenantExitProvider.ExportReceptionRow> rows = QuarkusTransaction.requiringNew().call(() -> {
                    access.enterAsPlatform(tenantId);
                    return exits.getExportReceptions(from, PART_DOCUMENTS);
                });
                if (rows.isEmpty()) {
                    return parts;
                }
                Map<String, byte[]> entries = new LinkedHashMap<>();
                for (TenantExitProvider.ExportReceptionRow r : rows) {
                    String name = "recepciones/" + r.ruc() + "/" + r.supplierRuc() + "-" + r.tipoComprobante() + "-" + r.serie() + "-" + r.numero();
                    entries.put(name + ".xml", files.get(r.xmlRef()));
                    if (r.pdfRef() != null) {
                        entries.put(name + ".pdf", files.get(r.pdfRef()));
                    }
                    if (r.cdrRef() != null) {
                        entries.put("R-" + name.replace("recepciones/", "recepciones/R-") + ".zip", files.get(r.cdrRef()));
                    }
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("reception_id", r.id().toString());
                    meta.put("ruc", r.ruc());
                    meta.put("supplier_ruc", r.supplierRuc());
                    meta.put("serie_numero", r.serie() + "-" + r.numero());
                    meta.put("fecha_emision", String.valueOf(r.fechaEmision()));
                    meta.put("validity_state", r.validityState());
                    index.add(meta);
                }
                FileStore.Stored stored = files.put(tenantId, zip(entries));
                parts.add(Map.of("name", String.format("recepciones-%04d.zip", parts.size() + 1), "ref", stored.key(), "size", stored.size(),
                        "documents", rows.size()));
                after = rows.get(rows.size() - 1).id();
            }
        }

        static byte[] zip(Map<String, byte[]> entries) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    out.putNextEntry(new ZipEntry(e.getKey()));
                    out.write(e.getValue());
                    out.closeEntry();
                }
            }
            return bytes.toByteArray();
        }
    }

    /**
     * Deletes the data of tenants whose exit period is over (daily): every file under the tenant's
     * storage prefix (referenced or not), then every row, keeping the tenant row without personal
     * data and the evidence in {@code tenant_deletion}.
     */
    @ApplicationScoped
    public static class Deletion implements JobHandler {
        private final TenantExitProvider exits;
        private final FileStore files;

        public Deletion(TenantExitProvider exits, FileStore files) {
            this.exits = exits;
            this.files = files;
        }

        @Override
        public String name() {
            return "tenants.exit-deletion";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            List<UUID> due = QuarkusTransaction.requiringNew().call(exits::getTenantsToDelete);
            int deleted = 0;
            for (UUID tenantId : due) {
                int filesDeleted = 0;
                for (String key : files.list(FileStore.keyOf(tenantId, ""))) {
                    files.delete(key);
                    filesDeleted++;
                }
                int count = filesDeleted;
                String counts = QuarkusTransaction.requiringNew().call(() -> exits.purgeTenant(tenantId, count));
                Logger.getLogger("xhandler.audit.platform").infof("tenant %s deleted after its exit period: %s, %d files", tenantId, counts, count);
                deleted++;
            }
            return Map.of("deleted", deleted);
        }
    }
}
