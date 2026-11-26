package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.models.MaintenanceProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Retention (DESIGN.md §13): documents older than the tenant's retention years (10 by default,
 * counted in full years after the issue year) are deleted with their events, files and links,
 * never while a tax audit holds the tenant. Daily, in batches; the database decides what expired.
 */
@ApplicationScoped
public class RetentionPurge implements JobHandler {

    private static final Logger LOG = Logger.getLogger(RetentionPurge.class);

    private final MaintenanceProvider maintenance;
    private final FileStore files;

    public RetentionPurge(MaintenanceProvider maintenance, FileStore files) {
        this.maintenance = maintenance;
        this.files = files;
    }

    @Override
    public String name() {
        return "retention.purge";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        int documents = 0;
        int deletedFiles = 0;
        for (int batch = 0; batch < 20; batch++) {
            List<MaintenanceProvider.ExpiredDocument> expired = QuarkusTransaction.requiringNew().call(maintenance::getExpiredDocuments);
            if (expired.isEmpty()) {
                break;
            }
            Map<UUID, List<UUID>> byTenant = new LinkedHashMap<>();
            for (MaintenanceProvider.ExpiredDocument r : expired) {
                byTenant.computeIfAbsent(r.tenantId(), k -> new ArrayList<>()).add(r.documentId());
            }
            for (Map.Entry<UUID, List<UUID>> e : byTenant.entrySet()) {
                List<String> keys = QuarkusTransaction.requiringNew().call(() -> maintenance.purgeDocuments(e.getKey(), e.getValue()));
                documents += e.getValue().size();
                for (String key : keys) {
                    try {
                        files.delete(key);
                        deletedFiles++;
                    } catch (RuntimeException ex) {
                        LOG.warnf("retention could not delete %s: %s", key, ex.toString());
                    }
                }
            }
        }
        if (documents > 0) {
            Logger.getLogger("xhandler.audit.platform").infof("retention deleted %d documents and %d files", documents, deletedFiles);
        }
        return Map.of("documents", documents, "files", deletedFiles);
    }
}
