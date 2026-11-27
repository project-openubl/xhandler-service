package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.jobs.JobConfig;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.jobs.JobStore;
import io.github.projectopenubl.xhandler.models.MaintenanceProvider;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** Platform maintenance jobs run by schedules (DESIGN.md §10.6). */
public final class PlatformJobs {

    static final ZoneId LIMA = ZoneId.of("America/Lima");

    private PlatformJobs() {}

    /** Creates upcoming partitions of document and document_event (§25). */
    @ApplicationScoped
    public static class Partitions implements JobHandler {

        private final MaintenanceProvider maintenance;

        public Partitions(MaintenanceProvider maintenance) {
            this.maintenance = maintenance;
        }

        @Override
        public String name() {
            return "platform.partitions";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            LocalDate today = LocalDate.now(LIMA);
            QuarkusTransaction.requiringNew().run(() -> maintenance.ensurePartitions(today));
            return Map.of("day", today.toString());
        }
    }

    /** Removes finished jobs past the protection period (§10.4). */
    @ApplicationScoped
    public static class JobPurge implements JobHandler {

        private final JobStore store;
        private final JobConfig config;

        public JobPurge(JobStore store, JobConfig config) {
            this.store = store;
            this.config = config;
        }

        @Override
        public String name() {
            return "platform.job-purge";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            int purged = QuarkusTransaction.requiringNew().call(() -> store.purge(config.purgeAge()));
            return Map.of("purged", purged);
        }
    }

    /** Idempotency keys are kept 24 hours (§11.1). */
    @ApplicationScoped
    public static class IdempotencyPurge implements JobHandler {

        private final MaintenanceProvider maintenance;

        public IdempotencyPurge(MaintenanceProvider maintenance) {
            this.maintenance = maintenance;
        }

        @Override
        public String name() {
            return "platform.idempotency-purge";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            int purged = QuarkusTransaction.requiringNew().call(maintenance::purgeIdempotencyKeys);
            return Map.of("purged", purged);
        }
    }
}
