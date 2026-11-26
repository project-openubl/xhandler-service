package io.github.projectopenubl.xhandler.jobs.handlers;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.models.TenantExitProvider;
import io.github.projectopenubl.xhandler.pdf.DocumentPdf;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.jboss.logging.Logger;

/**
 * Builds the export page by page: parts of at most {@link #PART_DOCUMENTS} documents (XML, CDR,
 * PDF and JSON each, named {@code env/RUC-TT-SERIE-NUMERO} like SUNAT), parts of receptions, and
 * an index. Memory stays bounded by one part.
 */
@ApplicationScoped
public class TenantExportJob implements JobHandler {
    public static final String NAME = "tenants.export";
    static final int PART_DOCUMENTS = 300;
    private static final ObjectMapper JSON = JsonMapper.builderWithJackson2Defaults().build();

    private final TenantAccess access;
    private final TenantExitProvider exits;
    private final FileStore files;
    private final DocumentPdf pdf;

    public TenantExportJob(TenantAccess access, TenantExitProvider exits, FileStore files, DocumentPdf pdf) {
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
            Logger.getLogger(TenantExportJob.class).errorf(e, "export %s failed", exportId);
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
