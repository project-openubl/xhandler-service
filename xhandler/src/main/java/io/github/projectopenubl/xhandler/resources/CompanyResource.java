package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.utils.RepresentationToModel;
import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.UpdateSeriesRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SeriesRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateSeriesRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CreateCompanyRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.CompanyRepresentation;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.SeriesProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.DefaultSeries;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.SeriesModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Constraints;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.managers.IdempotencyManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Companies (RUCs) of a tenant and their series. */
@Path("/api/v1/tenants/{tenantId}/companies")
public class CompanyResource {

    private final RepresentationToModel representationToModel;

    private final ModelToRepresentation modelToRepresentation;

    private final CompanyManager companyManager;

    private final SeriesProvider seriesProvider;

    private final CompanyProvider companyProvider;







    private final TenantAccess access;
    private final AuditManager auditor;
    private final IdempotencyManager idempotency;

    public CompanyResource(
            TenantAccess access,
            AuditManager auditor,
            IdempotencyManager idempotency,
            CompanyProvider companyProvider, SeriesProvider seriesProvider, CompanyManager companyManager, ModelToRepresentation modelToRepresentation, RepresentationToModel representationToModel) {
        this.representationToModel = representationToModel;
        this.modelToRepresentation = modelToRepresentation;
        this.companyManager = companyManager;
        this.seriesProvider = seriesProvider;
        this.companyProvider = companyProvider;
        this.access = access;
        this.auditor = auditor;
        this.idempotency = idempotency;
    }

    @APIResponse(responseCode = "201", description = "Empresa creada (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompanyRepresentation.class)))
    @APIResponse(responseCode = "409", description = "Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createCompany")
    @POST
    public Response create(@PathParam("tenantId") UUID tenantId, @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid CreateCompanyRepresentation request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("company", tenantId, request), () -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            CompanyModel company = companyProvider.newCompany(Ids.newId());
            company.setTenantId(tenantId);
            representationToModel.updateCompany(request, company);
            // Unverified duplicates of a RUC are allowed: the RUC is claimed at verification (P3)
            companyProvider.persist(company);
            for (DefaultSeries.Entry entry : DefaultSeries.ENTRIES) {
                SeriesModel series = seriesProvider.newSeries(Ids.newId());
                series.setTenantId(tenantId);
                series.setCompanyId(company.getId());
                series.setSunatEnv(company.getSunatEnv());
                series.setTipoComprobante(entry.tipoComprobante());
                series.setSerie(entry.serie());
                series.setDefault(true);
                seriesProvider.persist(series);
            }
            auditor.record(scope, "company.created", "company", company.getId(), Map.of("ruc", company.getRuc()));
            return modelToRepresentation.toRepresentation(company);
        }, view -> view);
    }

    @Operation(operationId = "listCompanies")
    @GET
    @Transactional
    public PageRepresentation<CompanyRepresentation> list(
            @PathParam("tenantId") UUID tenantId, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = PageRepresentation.limit(limit);
        int from = PageRepresentation.offset(offset);
        List<CompanyModel> companies = companyProvider.getCompanies(from, size);
        return new PageRepresentation<>(companies.stream().map(modelToRepresentation::toRepresentation).toList(), size, from);
    }

    @Operation(operationId = "getCompany")
    @GET
    @Path("/{companyId}")
    @Transactional
    public CompanyRepresentation get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        return modelToRepresentation.toRepresentation(companyManager.getCompany(companyId));
    }

    @Operation(operationId = "listSeries")
    @GET
    @Path("/{companyId}/series")
    @Transactional
    public List<SeriesRepresentation> series(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        CompanyModel company = companyManager.getCompany(companyId);
        List<SeriesModel> series = seriesProvider.getSeries(company.getId(), company.getSunatEnv());
        return series.stream().map(modelToRepresentation::toRepresentation).toList();
    }

    @APIResponse(responseCode = "201", description = "Serie creada (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SeriesRepresentation.class)))
    @APIResponse(responseCode = "409", description = "La serie ya existe, o Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createSeries")
    @POST
    @Path("/{companyId}/series")
    public Response createSeries(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid CreateSeriesRepresentation request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("series", companyId, request), () -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            CompanyModel company = companyManager.getCompany(companyId);
            SeriesModel series = seriesProvider.newSeries(Ids.newId());
            series.setTenantId(tenantId);
            series.setCompanyId(company.getId());
            series.setSunatEnv(company.getSunatEnv());
            // Onboarding from another provider: continue its numbering (DESIGN.md §9.2.1, 1033)
            representationToModel.updateSeries(request, series);
            try {
                seriesProvider.persist(series);
                seriesProvider.flush();
            } catch (PersistenceException e) {
                if (Constraints.violated(e).isPresent()) {
                    throw ApiException.conflict("series_exists", "La serie ya existe.");
                }
                throw e;
            }
            auditor.record(scope, "series.created", "series", series.getId(),
                    Map.of("tipo_comprobante", series.getTipoComprobante(), "serie", series.getSerie(), "next_number", series.getNextNumber()));
            return modelToRepresentation.toRepresentation(series);
        }, view -> view);
    }

    /**
     * Moves a series' next number forward or (de)activates it (DESIGN.md §8, §10.8). Each change is
     * a compare-and-set on the series row, which an emit holds locked until it commits: a number is
     * never handed out twice, and once deactivation commits no emit can take the series.
     */
    @APIResponse(responseCode = "200", description = "La serie actualizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SeriesRepresentation.class)))
    @APIResponse(responseCode = "409", description = "El siguiente número retrocedería por debajo de los ya usados.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "updateSeries", summary = "Cambia el siguiente número de una serie, o la activa o desactiva")
    @PUT
    @Path("/{companyId}/series/{seriesId}")
    @Transactional
    public SeriesRepresentation updateSeries(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("seriesId") UUID seriesId, @Valid UpdateSeriesRepresentation request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        companyManager.getCompany(companyId);
        if (!seriesProvider.seriesExists(companyId, seriesId)) {
            throw ApiException.notFound();
        }
        Map<String, Object> changes = new java.util.LinkedHashMap<>();
        if (request.nextNumber() != null) {
            // Forward only, against the counter as it is when the row lock is ours
            int moved = seriesProvider.moveNextNumber(seriesId, request.nextNumber());
            if (moved == 0) {
                throw ApiException.conflict("series_number_used",
                        "La serie ya usó números hasta " + (current(seriesId).getNextNumber() - 1) + "; el siguiente número no puede retroceder.");
            }
            changes.put("next_number", request.nextNumber());
        }
        if (request.active() != null) {
            seriesProvider.setActive(seriesId, request.active());
            changes.put("active", request.active());
        }
        if (!changes.isEmpty()) {
            auditor.record(scope, request.active() != null && !request.active() ? "series.deactivated" : "series.updated", "series", seriesId,
                    changes);
        }
        return modelToRepresentation.toRepresentation(current(seriesId));
    }

    /** The series row as committed so far in this transaction (the native updates bypass the session). */
    private SeriesModel current(UUID seriesId) {
        SeriesModel series = seriesProvider.getSeries(seriesId).orElse(null);
        seriesProvider.refresh(series);
        return series;
    }



}
