package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.jpa.entities.SupportSessionEntity;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * Support access (DESIGN.md §26.1), tenant side: the tenant's owner or an admin approves or revokes
 * an operator's session; while approved, the operator has read-only access. All audited. Operators
 * request sessions through {@link SupportRequestResource}.
 */
@Path("/api/v1/tenants/{tenantId}/support-sessions")
public class SupportResource {

    static final Duration SESSION_LENGTH = Duration.ofHours(4);

    public record SessionView(UUID id, String operator, String reason, Instant requestedAt, String approvedBy, Instant expiresAt, Instant revokedAt) {}

    private final TenantAccess access;
    private final Auditor auditor;

    public SupportResource(TenantAccess access, Auditor auditor) {
        this.access = access;
        this.auditor = auditor;
    }

    @Operation(operationId = "listSupportSessions")
    @GET
    @Transactional
    public List<SessionView> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.ADMIN);
        List<SupportSessionEntity> sessions = SupportSessionEntity.list("order by requestedAt desc");
        return sessions.stream().map(SupportResource::view).toList();
    }

    @Operation(operationId = "approveSupportSession")
    @POST
    @Path("/{sessionId}/approve")
    @Transactional
    public SessionView approve(@PathParam("tenantId") UUID tenantId, @PathParam("sessionId") UUID sessionId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        SupportSessionEntity session = find(sessionId);
        if (session.getApprovedAt() != null || session.getRevokedAt() != null) {
            throw ApiException.conflict("session_closed", "La sesión de soporte ya fue aprobada o revocada.");
        }
        session.setApprovedBy(scope.actor().auditName());
        session.setApprovedAt(Instant.now());
        session.setExpiresAt(session.getApprovedAt().plus(SESSION_LENGTH));
        auditor.record(scope, "support_session.approved", "support_session", session.getId(), Map.of("expires_at", session.getExpiresAt().toString()));
        return view(session);
    }

    @Operation(operationId = "revokeSupportSession")
    @POST
    @Path("/{sessionId}/revoke")
    @Transactional
    public SessionView revoke(@PathParam("tenantId") UUID tenantId, @PathParam("sessionId") UUID sessionId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        SupportSessionEntity session = find(sessionId);
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            auditor.record(scope, "support_session.revoked", "support_session", session.getId());
        }
        return view(session);
    }

    private static SupportSessionEntity find(UUID id) {
        SupportSessionEntity session = SupportSessionEntity.findById(id);
        if (session == null) {
            throw ApiException.notFound();
        }
        return session;
    }

    private static SessionView view(SupportSessionEntity s) {
        return new SessionView(s.getId(), s.getOperator(), s.getReason(), s.getRequestedAt(), s.getApprovedBy(), s.getExpiresAt(), s.getRevokedAt());
    }
}
