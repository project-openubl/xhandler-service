package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.TenantUsageRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.PartnerTenantRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.PartnerRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.PartnerCreateTenantRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.PartnerBrandingRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.BrandingRepresentation;
import io.github.projectopenubl.xhandler.managers.LogoManager;
import io.github.projectopenubl.xhandler.models.PartnerProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.CurrentActor;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tools.jackson.core.JacksonException;

/**
 * The partner API (DESIGN.md §1.2, §14): software vendors who resell the service create and manage
 * their tenants, see their usage, and brand PDFs and emails (white-label). Partner admins act as
 * admins of their tenants (TenantAccess); partner viewers can only look.
 */
@Path("/api/v1/partners")
public class PartnerResource {







    private final CurrentActor currentActor;
    private final PartnerProvider partners;
    private final tools.jackson.databind.ObjectMapper json;
    private final FileStore files;

    public PartnerResource(CurrentActor currentActor, PartnerProvider partners, tools.jackson.databind.ObjectMapper json, FileStore files) {
        this.currentActor = currentActor;
        this.partners = partners;
        this.json = json;
        this.files = files;
    }

    @Operation(operationId = "listMyPartners")
    @GET
    @Transactional
    public List<PartnerRepresentation> mine() {
        Actor actor = currentActor.get();
        return partners.getPartnersOf(actor.kind(), actor.subject()).stream()
                .map(r -> new PartnerRepresentation(r.id(), r.name(), r.role())).toList();
    }

    @APIResponse(responseCode = "201", description = "Cuenta creada para el socio.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PartnerTenantRepresentation.class)))
    @Operation(operationId = "createPartnerTenant")
    @POST
    @Path("/{partnerId}/tenants")
    @Transactional
    public Response createTenant(@PathParam("partnerId") UUID partnerId, @Valid PartnerCreateTenantRepresentation request) {
        Actor actor = require(partnerId, "admin");
        UUID id = Ids.newId();
        partners.createPartnerTenant(partnerId, id, request.name().strip(), actor.auditName(),
                request.ownerSubject() == null ? null : (request.ownerKind() == null ? "user" : request.ownerKind()),
                request.ownerSubject());
        return Response.status(201).entity(new PartnerTenantRepresentation(id, request.name().strip(), partnerId)).build();
    }

    /** The partner's tenants with their documents in a month (default: this month, Lima). */
    @Operation(operationId = "listPartnerTenants")
    @GET
    @Path("/{partnerId}/tenants")
    @Transactional
    public List<TenantUsageRepresentation> tenants(@PathParam("partnerId") UUID partnerId, @QueryParam("month") String month) {
        require(partnerId, "viewer");
        YearMonth period;
        try {
            period = month == null ? YearMonth.now(io.github.projectopenubl.xhandler.utils.Deadlines.LIMA) : YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.invalid("invalid_request", "month debe tener el formato AAAA-MM.", null);
        }
        Instant from = period.atDay(1).atStartOfDay(io.github.projectopenubl.xhandler.utils.Deadlines.LIMA).toInstant();
        Instant to = period.plusMonths(1).atDay(1).atStartOfDay(io.github.projectopenubl.xhandler.utils.Deadlines.LIMA).toInstant();
        return partners.getPartnerTenants(partnerId, from, to).stream().map(r -> new TenantUsageRepresentation(r.id(), r.name(), r.status(), r.plan(),
                r.productionDocuments(), r.otherDocuments())).toList();
    }

    @Operation(operationId = "getPartnerBranding")
    @GET
    @Path("/{partnerId}/branding")
    @Transactional
    public PartnerBrandingRepresentation getBranding(@PathParam("partnerId") UUID partnerId) {
        require(partnerId, "viewer");
        return brandingView(partnerId);
    }

    @Operation(operationId = "setPartnerBranding")
    @PUT
    @Path("/{partnerId}/branding")
    @Transactional
    public PartnerBrandingRepresentation branding(@PathParam("partnerId") UUID partnerId, @Valid BrandingRepresentation branding) {
        require(partnerId, "admin");
        String brandingJson;
        try {
            brandingJson = json.writeValueAsString(branding);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
        partners.setPartnerBranding(partnerId, brandingJson);
        return new PartnerBrandingRepresentation(partnerId, branding, brandingView(partnerId).hasLogo());
    }

    /**
     * The partner's logo for the PDFs of its tenants' companies that have none: PNG or JPEG, up to
     * 200 KB, like a company logo. Stored under the partner's id (partner and tenant ids are both
     * random UUIDs, so it never lands in a tenant's files).
     */
    @Operation(operationId = "uploadPartnerLogo")
    @PUT
    @Path("/{partnerId}/logo")
    @jakarta.ws.rs.Consumes({"image/png", "image/jpeg"})
    @Transactional
    public PartnerBrandingRepresentation uploadLogo(@PathParam("partnerId") UUID partnerId, byte[] image) {
        require(partnerId, "admin");
        String ref = files.put(partnerId, LogoManager.normalize(image)).key();
        partners.setPartnerLogo(partnerId, ref);
        return brandingView(partnerId);
    }

    @APIResponse(responseCode = "204", description = "Logo del socio eliminado.")
    @Operation(operationId = "deletePartnerLogo")
    @DELETE
    @Path("/{partnerId}/logo")
    @Transactional
    public Response deleteLogo(@PathParam("partnerId") UUID partnerId) {
        require(partnerId, "admin");
        partners.clearPartnerLogo(partnerId);
        return Response.noContent().build();
    }

    private PartnerBrandingRepresentation brandingView(UUID partnerId) {
        PartnerProvider.PartnerBranding row = partners.getPartnerBranding(partnerId);
        BrandingRepresentation branding;
        try {
            branding = row.brandingJson() == null ? new BrandingRepresentation(null, null, null) : json.readValue(row.brandingJson(), BrandingRepresentation.class);
        } catch (JacksonException e) {
            // Branding written before the record's shape: shown empty rather than failing
            branding = new BrandingRepresentation(null, null, null);
        }
        return new PartnerBrandingRepresentation(partnerId, branding, row.hasLogo());
    }

    /** The caller must be a member of the partner with at least {@code role} (admin > viewer). Unknown partners are 404. */
    private Actor require(UUID partnerId, String role) {
        Actor actor = currentActor.get();
        String mine = mine().stream().filter(p -> p.id().equals(partnerId)).map(PartnerRepresentation::role).findFirst()
                .orElseThrow(ApiException::notFound);
        if ("admin".equals(role) && !"admin".equals(mine)) {
            throw ApiException.forbidden();
        }
        return actor;
    }

    static LocalDate today() {
        return io.github.projectopenubl.xhandler.utils.Deadlines.todayInLima();
    }
}
