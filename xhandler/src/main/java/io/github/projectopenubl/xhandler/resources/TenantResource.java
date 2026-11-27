package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.utils.RepresentationToModel;
import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.UpdateTenantRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.TenantSummaryRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.TenantRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateTenantRepresentation;
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
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.models.MembershipProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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

/** Tenants of the caller, and self-service signup (DESIGN.md §6). */
@Path("/api/v1/tenants")
public class TenantResource {

    private final RepresentationToModel representationToModel;

    private final ModelToRepresentation modelToRepresentation;

    private final TenantMemberProvider tenantMemberProvider;

    private final TenantProvider tenantProvider;

    private final MembershipProvider membership;
    private final CurrentActor currentActor;
    private final TenantAccess access;
    private final AuditManager auditor;

    public TenantResource(MembershipProvider membership, CurrentActor currentActor, TenantAccess access, AuditManager auditor, TenantProvider tenantProvider, TenantMemberProvider tenantMemberProvider, ModelToRepresentation modelToRepresentation, RepresentationToModel representationToModel) {
        this.representationToModel = representationToModel;
        this.modelToRepresentation = modelToRepresentation;
        this.tenantMemberProvider = tenantMemberProvider;
        this.tenantProvider = tenantProvider;
        this.membership = membership;
        this.currentActor = currentActor;
        this.access = access;
        this.auditor = auditor;
    }

    @Operation(operationId = "listTenants")
    @GET
    @Transactional
    public List<TenantSummaryRepresentation> list() {
        Actor actor = currentActor.get();
        return membership.getTenantsOf(actor.kind(), actor.subject()).stream()
                .map(r -> new TenantSummaryRepresentation(r.id(), r.name(), r.role()))
                .toList();
    }

    /** Self-service signup: a person creates a tenant and becomes its owner. */
    @APIResponse(responseCode = "201", description = "Cuenta creada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TenantRepresentation.class)))
    @Operation(operationId = "createTenant")
    @POST
    @Transactional
    public Response create(@Valid CreateTenantRepresentation request) {
        Actor actor = currentActor.get();
        if (!actor.isUser()) {
            throw ApiException.forbidden();
        }
        if (!actor.emailVerified()) {
            throw new ApiException(403, "email_not_verified", "Debe verificar su correo electrónico antes de crear una cuenta.");
        }

        UUID tenantId = Ids.newId();
        access.setTenant(tenantId);

        TenantModel tenant = tenantProvider.newTenant(tenantId);
        representationToModel.updateTenant(request, tenant);
        tenant.setCreatedBy(actor.subject());
        tenantProvider.persist(tenant);

        TenantMemberModel owner = tenantMemberProvider.newTenantMember(Ids.newId());
        owner.setTenantId(tenantId);
        owner.setKind(actor.kind());
        owner.setSubject(actor.subject());
        owner.setRole(Role.OWNER.json());
        tenantMemberProvider.persist(owner);

        TenantScope scope = new TenantScope(tenantId, actor, Role.OWNER);
        auditor.record(scope, "tenant.created", "tenant", tenantId);
        return Response.status(201).entity(modelToRepresentation.toRepresentation(tenant)).build();
    }

    @Operation(operationId = "getTenant")
    @GET
    @Path("/{tenantId}")
    @Transactional
    public TenantRepresentation get(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        TenantModel tenant = tenantProvider.getTenant(tenantId).orElse(null);
        if (tenant == null) {
            throw ApiException.notFound();
        }
        return modelToRepresentation.toRepresentation(tenant);
    }

    /** Renames the tenant (its organization name in the console, emails and partner reports). */
    @Operation(operationId = "updateTenant")
    @PUT
    @Path("/{tenantId}")
    @Transactional
    public TenantRepresentation update(@PathParam("tenantId") UUID tenantId, @Valid UpdateTenantRepresentation request) {
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
        return modelToRepresentation.toRepresentation(tenant);
    }

}
