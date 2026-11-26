package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.ReceptionProvider;
import io.github.projectopenubl.xhandler.models.DocumentModel;

import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;

import tools.jackson.databind.json.JsonMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;
import tools.jackson.core.JacksonException;

/**
 * Completes the amounts by tax of documents and receptions stored before they were recorded
 * (DESIGN.md §11.9): reads the stored XML, asks the engine, records them. Reports and SIRE refuse
 * to run on rows without them rather than write zeros. File and engine I/O happen outside
 * transactions (§10.8).
 */
@ApplicationScoped
public class AmountsBackfill implements JobHandler {

    private final ReceptionProvider receptionProvider;

    private static final Logger LOG = Logger.getLogger(AmountsBackfill.class);
    private static final tools.jackson.databind.ObjectMapper JSON = JsonMapper.builderWithJackson2Defaults().build();

    private final DocumentProvider documentProvider;
    private final TenantAccess access;
    private final Engine engine;
    private final FileStore files;

    public AmountsBackfill(DocumentProvider documentProvider, TenantAccess access, Engine engine, FileStore files, ReceptionProvider receptionProvider) {
        this.receptionProvider = receptionProvider;
        this.documentProvider = documentProvider;
        this.access = access;
        this.engine = engine;
        this.files = files;
    }

    @Override
    public String name() {
        return "documents.amounts-backfill";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record Item(UUID tenant, UUID id, boolean reception) {}
        List<Item> items = QuarkusTransaction.requiringNew().call(() -> {
            List<DocumentProvider.TenantRow> docs = documentProvider.getDocumentsWithoutAmounts();
            List<ReceptionProvider.ReceptionRef> receptions = receptionProvider.getReceptionsWithoutAmounts();
            return java.util.stream.Stream.concat(docs.stream().map(r -> new Item(r.tenantId(), r.id(), false)),
                    receptions.stream().map(r -> new Item(r.tenantId(), r.id(), true))).toList();
        });
        int done = 0;
        for (Item item : items) {
            try {
                if (fill(item.tenant(), item.id(), item.reception())) {
                    done++;
                }
            } catch (RuntimeException e) {
                // Marked, so it doesn't block the queue; reports treat it as missing, never as zeros
                LOG.warnf("amounts of %s %s: %s", item.reception() ? "reception" : "document", item.id(), e.toString());
                markUnavailable(item.tenant(), item.id(), item.reception());
            }
        }
        return Map.of("pending", items.size(), "filled", done);
    }

    private void markUnavailable(UUID tenantId, UUID id, boolean reception) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            if (reception) {
                receptionProvider.markReceptionAmountsUnreadable(id);
            } else {
                documentProvider.markAmountsUnreadable(id);
            }
        });
    }

    private boolean fill(UUID tenantId, UUID id, boolean reception) {
        String key = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            return (reception ? receptionProvider.getReceptionXmlRef(id) : documentProvider.getSignedXmlStorageKey(id)).orElse(null);
        });
        if (key == null) {
            throw new IllegalStateException("no stored XML");
        }
        EngineModels.InspectResponse identity = engine.inspect(new String(files.get(key), StandardCharsets.UTF_8));
        Map<String, String> amounts = DocumentModel.amountsOf(identity.amounts());
        String json;
        try {
            json = JSON.writeValueAsString(amounts == null ? Map.of() : amounts);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            if (reception) {
                receptionProvider.setReceptionAmounts(id, json);
            } else {
                documentProvider.setDocumentAmounts(id, json, identity.paymentTerms() == null ? "contado" : identity.paymentTerms());
            }
        });
        return true;
    }
}
