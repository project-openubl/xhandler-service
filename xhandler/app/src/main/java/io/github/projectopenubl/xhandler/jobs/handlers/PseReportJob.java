package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.resources.admin.AdminLog;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.models.PsePlatformProvider;
import io.github.projectopenubl.xhandler.resources.admin.PseAdminResource;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Computes the current and previous half-years, keeps them, and exposes the rate as a metric. */
@ApplicationScoped
public class PseReportJob implements JobHandler {

    private final PsePlatformProvider pse;
    private final Optional<LocalDate> registeredOn;

    public PseReportJob(PsePlatformProvider pse, MeterRegistry metrics,
            @ConfigProperty(name = "xhandler.pse.registered-on") Optional<LocalDate> registeredOn) {
        this.pse = pse;
        this.registeredOn = registeredOn;
        // Read from the database on scrape: every pod reports the same value, also after a restart
        io.micrometer.core.instrument.Gauge.builder("xhandler.pse.rejected_rate", this, PseReportJob::latestRate).register(metrics);
    }

    double latestRate() {
        try {
            Optional<Double> rate = QuarkusTransaction.requiringNew().call(pse::getLatestReportRate);
            return rate.orElse(0.0);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    @Override
    public String name() {
        return "platform.pse-report";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        LocalDate today = io.github.projectopenubl.xhandler.utils.Deadlines.todayInLima();
        String now = periodOf(today);
        String previous = periodOf(today.minusMonths(6));
        for (String period : List.of(previous, now)) {
            QuarkusTransaction.requiringNew().run(() -> {
                PseAdminResource.Rate rate = PseAdminResource.rate(pse, period, registeredOn);
                pse.saveReport(period, rate.accepted(), rate.rejected(), rate.rate());
                if (rate.breached()) {
                    AdminLog.PLATFORM_AUDIT.errorf("PSE rejected-CDR rate %s in %s is above the %s limit", rate.rate(), period,
                            rate.limit());
                }
            });
        }
        return Map.of("period", now, "at", Instant.now().toString());
    }

    public static String periodOf(LocalDate day) {
        return day.getYear() + (day.getMonthValue() <= 6 ? "-H1" : "-H2");
    }
}
