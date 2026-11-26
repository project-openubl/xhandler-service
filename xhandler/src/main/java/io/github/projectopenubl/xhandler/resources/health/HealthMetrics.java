package io.github.projectopenubl.xhandler.resources.health;

import io.github.projectopenubl.xhandler.models.MetricsProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Gauges for the SLO alerts (DESIGN.md §18): documents that need a person (production, not held,
 * active tenants: exception in the last day, unknown for over an hour, past their SUNAT deadline),
 * documents waiting for SUNAT and the backlog of ready jobs. The {@code ops.snapshot} job computes
 * them once every 30 seconds for the whole cluster; every pod's gauges read that small table on
 * scrape (NaN when the database doesn't answer or the snapshot is older than 5 minutes).
 */
@Startup
@ApplicationScoped
public class HealthMetrics {

    public static final List<String> NAMES = List.of("documents.exception", "documents.unknown_stale", "documents.past_deadline", "documents.pending",
            "jobs.waiting", "jobs.oldest_waiting_seconds");

    private final MetricsProvider metricsProvider;

    public HealthMetrics(MetricsProvider metricsProvider, MeterRegistry metrics) {
        this.metricsProvider = metricsProvider;
        for (String name : NAMES) {
            Gauge.builder("xhandler." + name, this, h -> h.read(name)).register(metrics);
        }
    }

    double read(String name) {
        try {
            return QuarkusTransaction.requiringNew().call(() -> metricsProvider.getSnapshotValue(name)).orElse(Double.NaN);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

}
