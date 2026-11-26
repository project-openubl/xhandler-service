package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.models.TenantExitProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Deletes the data of tenants whose exit period is over (daily): every file under the tenant's
 * storage prefix (referenced or not), then every row, keeping the tenant row without personal
 * data and the evidence in {@code tenant_deletion}.
 */
@ApplicationScoped
public class TenantDeletionJob implements JobHandler {
    private final TenantExitProvider exits;
    private final FileStore files;

    public TenantDeletionJob(TenantExitProvider exits, FileStore files) {
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
