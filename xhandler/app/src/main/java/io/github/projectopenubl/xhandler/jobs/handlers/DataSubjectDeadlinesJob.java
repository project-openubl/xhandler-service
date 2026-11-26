package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.managers.NotificationManager;
import io.github.projectopenubl.xhandler.models.PrivacyProvider;
import io.github.projectopenubl.xhandler.resources.DataSubjectRequests;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reminds tenants 2 days before the term; overdue requests are logged for the platform (daily). */
@ApplicationScoped
public class DataSubjectDeadlinesJob implements JobHandler {
    private final PrivacyProvider privacy;
    private final TenantAccess access;
    private final NotificationManager notifications;

    public DataSubjectDeadlinesJob(PrivacyProvider privacy, TenantAccess access, NotificationManager notifications) {
        this.privacy = privacy;
        this.access = access;
        this.notifications = notifications;
    }

    @Override
    public String name() {
        return "privacy.deadlines";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record Due(UUID tenant, UUID id, Instant dueAt) {}
        List<Due> due = QuarkusTransaction.requiringNew().call(() -> privacy.getRequestsDue().stream()
                .map(r -> new Due(r.tenantId(), r.requestId(), r.responseDueAt())).toList());
        int reminded = 0;
        for (Due d : due) {
            if (d.dueAt().isBefore(Instant.now())) {
                org.jboss.logging.Logger.getLogger("xhandler.audit.platform")
                        .warnf("data-subject request %s of tenant %s is overdue since %s", d.id(), d.tenant(), d.dueAt());
            }
            boolean sent = QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(d.tenant());
                int first = privacy.markReminded(d.id());
                if (first == 1) {
                    notifications.publish(d.tenant(), "privacy.deadline", "Solicitud de datos personales por vencer",
                            "Una solicitud de derechos de datos personales vence el " + DataSubjectRequests.lima(d.dueAt()) + ".", null);
                }
                return first == 1;
            });
            if (sent) {
                reminded++;
            }
        }
        return Map.of("due", due.size(), "reminded", reminded);
    }
}
