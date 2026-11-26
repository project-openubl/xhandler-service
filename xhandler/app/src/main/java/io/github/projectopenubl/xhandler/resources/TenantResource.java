package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.TenantMemberProvider;
import io.github.projectopenubl.xhandler.models.TenantProvider;
import io.github.projectopenubl.xhandler.models.TenantModel;
import io.github.projectopenubl.xhandler.models.TenantMemberModel;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.CurrentActor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.Auditor;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Tenants of the caller, and self-service signup (DESIGN.md §6, §26.8). */
@Path("/api/v1/tenants")
public class TenantResource {

    private final TenantMemberProvider tenantMemberProvider;

    private final TenantProvider tenantProvider;

    /** Free tenants a person may create (signup abuse control). */
    static final int MAX_FREE_TENANTS_PER_USER = 1;

    public record TenantSummary(UUID id, String name, String role) {}

    public record CreateTenant(@NotBlank @Size(max = 200) String name) {}

    public record UpdateTenant(@NotBlank @Size(max = 200) String name) {}

    public record TenantView(UUID id, String name, String plan, String status, int retentionYears) {}

    private final EntityManager em;
    private final CurrentActor currentActor;
    private final TenantAccess access;
    private final Auditor auditor;

    public TenantResource(EntityManager em, CurrentActor currentActor, TenantAccess access, Auditor auditor, TenantProvider tenantProvider, TenantMemberProvider tenantMemberProvider) {
        this.tenantMemberProvider = tenantMemberProvider;
        this.tenantProvider = tenantProvider;
        this.em = em;
        this.currentActor = currentActor;
        this.access = access;
        this.auditor = auditor;
    }

    @Operation(operationId = "listTenants")
    @GET
    @Transactional
    public List<TenantSummary> list() {
        Actor actor = currentActor.get();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, name, role FROM xhandler.tenants_of(:kind, :subject)")
                .setParameter("kind", actor.kind())
                .setParameter("subject", actor.subject())
                .getResultList();
        return rows.stream()
                .map(r -> new TenantSummary((UUID) r[0], (String) r[1], (String) r[2]))
                .toList();
    }

    /** Self-service signup: a person creates a free tenant and becomes its owner. */
    @APIResponse(responseCode = "201", description = "Cuenta creada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TenantView.class)))
    @Operation(operationId = "createTenant")
    @POST
    @Transactional
    public Response create(@Valid CreateTenant request) {
        Actor actor = currentActor.get();
        if (!actor.isUser()) {
            throw ApiException.forbidden();
        }
        if (!actor.emailVerified()) {
            throw new ApiException(403, "email_not_verified", "Debe verificar su correo electrónico antes de crear una cuenta.");
        }
        Number created = (Number) em.createNativeQuery("SELECT xhandler.free_tenants_created_by(:subject)")
                .setParameter("subject", actor.subject())
                .getSingleResult();
        if (created.longValue() >= MAX_FREE_TENANTS_PER_USER) {
            throw ApiException.conflict("limit_reached", "Ya tiene una cuenta gratuita.");
        }

        UUID tenantId = Ids.newId();
        access.setTenant(tenantId);

        TenantModel tenant = tenantProvider.newTenant(tenantId);
        tenant.setName(request.name().strip());
        tenant.setCreatedBy(actor.subject());
        try {
            tenantProvider.persist(tenant);
            tenantProvider.flush();
        } catch (jakarta.persistence.PersistenceException e) {
            // Two concurrent signups: the database keeps one free tenant per person
            if (io.github.projectopenubl.xhandler.utils.Constraints.violated(e).filter("tenant_one_free_per_person"::equals).isPresent()) {
                throw ApiException.conflict("limit_reached", "Ya tiene una cuenta gratuita.");
            }
            throw e;
        }

        TenantMemberModel owner = tenantMemberProvider.newTenantMember(Ids.newId());
        owner.setTenantId(tenantId);
        owner.setKind(actor.kind());
        owner.setSubject(actor.subject());
        owner.setRole(Role.OWNER.json());
        tenantMemberProvider.persist(owner);

        TenantScope scope = new TenantScope(tenantId, actor, Role.OWNER);
        auditor.record(scope, "tenant.created", "tenant", tenantId);
        return Response.status(201).entity(view(tenant)).build();
    }

    @Operation(operationId = "getTenant")
    @GET
    @Path("/{tenantId}")
    @Transactional
    public TenantView get(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        if (tenant == null) {
            throw ApiException.notFound();
        }
        return view(tenant);
    }

    /** Renames the tenant (its organization name in the console, emails and partner reports). */
    @Operation(operationId = "updateTenant")
    @PUT
    @Path("/{tenantId}")
    @Transactional
    public TenantView update(@PathParam("tenantId") UUID tenantId, @Valid UpdateTenant request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        TenantModel tenant = tenantProvider.getTenantForUpdate(tenantId).orElse(null);
        if (tenant == null) {
            throw ApiException.notFound();
        }
        String name = request.name().strip();
        if (!name.equals(tenant.getName())) {
            String previous = tenant.getName();
            tenant.setName(name);
            auditor.record(scope, "tenant.renamed", "tenant", tenantId, java.util.Map.of("from", previous, "to", name));
        }
        return view(tenant);
    }

    private static TenantView view(TenantModel tenant) {
        return new TenantView(tenant.getId(), tenant.getName(), tenant.getPlan(), tenant.getStatus(), tenant.getRetentionYears());
    }
}
