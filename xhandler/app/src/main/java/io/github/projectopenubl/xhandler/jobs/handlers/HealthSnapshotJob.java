package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.models.MetricsProvider;
import io.github.projectopenubl.xhandler.resources.health.HealthMetrics;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/** Computes the snapshot (scheduled every 30 seconds, one run in the cluster). */
@ApplicationScoped
public class HealthSnapshotJob implements JobHandler {
    private final MetricsProvider metricsProvider;

    public HealthSnapshotJob(MetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    @Override
    public String name() {
        return "ops.snapshot";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        QuarkusTransaction.requiringNew().run(() -> {
            MetricsProvider.DocumentHealth d = metricsProvider.getDocumentHealth();
            MetricsProvider.JobBacklog j = metricsProvider.getJobBacklog();
            double[] values = {d.exception(), d.unknownStale(), d.pastDeadline(), d.pending(), j.waiting(), j.oldestSeconds()};
            for (int i = 0; i < HealthMetrics.NAMES.size(); i++) {
                metricsProvider.saveSnapshotValue(HealthMetrics.NAMES.get(i), values[i]);
            }
        });
        return Map.of();
    }
}
