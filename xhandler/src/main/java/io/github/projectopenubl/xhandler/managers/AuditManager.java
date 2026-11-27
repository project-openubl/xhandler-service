package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

/**
 * Records changes and every access to personal data or secrets (DESIGN.md §16) as one log line on
 * the {@code xhandler.audit} logger, like Pulp's access log: the log pipeline keeps and searches
 * them, the service stores no audit table. The fields also go to the MDC ({@code audit.*}), so
 * JSON logs carry them as attributes next to the request's trace id.
 */
@ApplicationScoped
public class AuditManager {

    private static final Logger LOG = Logger.getLogger("xhandler.audit");

    public void record(TenantScope scope, String action, String objectType, Object objectId, Map<String, Object> details) {
        String tenant = String.valueOf(scope.tenantId());
        String actor = scope.actor().auditName();
        String object = objectId == null ? null : objectId.toString();
        MDC.put("audit.tenant", tenant);
        MDC.put("audit.actor", actor);
        MDC.put("audit.action", action);
        MDC.put("audit.object_type", objectType);
        if (object != null) {
            MDC.put("audit.object_id", object);
        }
        try {
            LOG.infof("tenant=%s actor=%s action=%s object=%s:%s details=%s", tenant, actor, action, objectType, object,
                    details == null ? Map.of() : details);
        } finally {
            MDC.remove("audit.tenant");
            MDC.remove("audit.actor");
            MDC.remove("audit.action");
            MDC.remove("audit.object_type");
            MDC.remove("audit.object_id");
        }
    }

    public void record(TenantScope scope, String action, String objectType, Object objectId) {
        record(scope, action, objectType, objectId, null);
    }
}
