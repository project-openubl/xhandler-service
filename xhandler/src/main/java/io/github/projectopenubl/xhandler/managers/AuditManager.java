package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.AuditLogProvider;
import io.github.projectopenubl.xhandler.models.AuditLogModel;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;

/**
 * Records changes and every access to personal data or secrets (DESIGN.md §16), in the same
 * transaction as the action.
 */
@ApplicationScoped
public class AuditManager {

    private final AuditLogProvider auditLogProvider;

    public AuditManager(AuditLogProvider auditLogProvider) {
        this.auditLogProvider = auditLogProvider;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(TenantScope scope, String action, String objectType, Object objectId, Map<String, Object> details) {
        AuditLogModel entry = auditLogProvider.newAuditLog(Ids.newId());
        entry.setAt(Instant.now());
        entry.setTenantId(scope.tenantId());
        entry.setActor(scope.actor().auditName() + (scope.role() == io.github.projectopenubl.xhandler.security.Role.SUPPORT ? " (support)" : ""));
        entry.setAction(action);
        entry.setObjectType(objectType);
        entry.setObjectId(objectId == null ? null : objectId.toString());
        entry.setDetails(details);
        auditLogProvider.persist(entry);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(TenantScope scope, String action, String objectType, Object objectId) {
        record(scope, action, objectType, objectId, null);
    }
}
