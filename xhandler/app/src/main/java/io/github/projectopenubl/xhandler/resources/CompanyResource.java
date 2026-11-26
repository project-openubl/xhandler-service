package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.SeriesProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.DefaultSeries;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.models.SeriesModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Constraints;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.Page;
import io.github.projectopenubl.xhandler.managers.Auditor;
import io.github.projectopenubl.xhandler.managers.Idempotency;
import io.github.projectopenubl.xhandler.managers.Numbering;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
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

    private final CompanyManager companyManager;

    private final SeriesProvider seriesProvider;

    private final CompanyProvider companyProvider;


    public record CreateCompany(
            @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener 11 dígitos") String ruc,
            @NotBlank @Size(max = 300) String razonSocial,
            @Size(max = 300) String nombreComercial) {}

    public record CompanyView(
            UUID id, String ruc, String razonSocial, String nombreComercial, String sunatEnv,
            String emissionMode, Instant rucVerifiedAt, String email, boolean deliveredOnEmitDefault, boolean sendingPaused,
            String direccion, String pdfFormat, boolean emailOnAccept, boolean hasLogo, String submissionRoute,
            String libroReclamacionesUrl,
            @Schema(description = "Asunto del correo al comprador; null usa el predeterminado.") String emailSubject,
            @Schema(description = "Cuerpo del correo al comprador; null usa el predeterminado.") String emailBody) {}

    public record SeriesView(UUID id, String sunatEnv, String tipoComprobante, String serie, long nextNumber, boolean isDefault, boolean active) {}

    public record CreateSeries(
            @NotBlank @Pattern(regexp = "\\d{2}") String tipoComprobante,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{4}", message = "debe tener 4 caracteres en mayúsculas o dígitos") String serie,
            @Min(1) Long nextNumber) {}

    /**
     * {@code nextNumber} only moves forward (numbers are never reused, DESIGN.md §8); {@code active}
     * false deactivates the series, so emits refuse it.
     */
    public record UpdateSeries(@Min(1) @Max(Numbering.MAX_NUMBER) Long nextNumber, Boolean active) {}

    private final TenantAccess access;
    private final Auditor auditor;
    private final Idempotency idempotency;
    /**
     * Unverified companies a tenant may have (DESIGN.md §26.8): RUCs are claimed only when verified,
     * and this keeps a free signup from registering RUCs in bulk.
     */
    private final long maxUnverifiedCompanies;

    public CompanyResource(
            TenantAccess access,
            Auditor auditor,
            Idempotency idempotency,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.limits.max-unverified-companies", defaultValue = "3")
                    long maxUnverifiedCompanies, CompanyProvider companyProvider, SeriesProvider seriesProvider, CompanyManager companyManager) {
        this.companyManager = companyManager;
        this.seriesProvider = seriesProvider;
        this.companyProvider = companyProvider;
        this.access = access;
        this.auditor = auditor;
        this.idempotency = idempotency;
        this.maxUnverifiedCompanies = maxUnverifiedCompanies;
    }

    @APIResponse(responseCode = "201", description = "Empresa creada (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompanyView.class)))
    @APIResponse(responseCode = "409", description = "Máximo de empresas sin verificar, o Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createCompany")
    @POST
    public Response create(@PathParam("tenantId") UUID tenantId, @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid CreateCompany request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("company", tenantId, request), () -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            if (companyProvider.countUnverifiedCompanies() >= maxUnverifiedCompanies) {
                throw ApiException.conflict("limit_reached", "Alcanzó el máximo de empresas sin verificar.");
            }
            CompanyModel company = companyProvider.newCompany(Ids.newId());
            company.setTenantId(tenantId);
            company.setRuc(request.ruc());
            company.setRazonSocial(request.razonSocial().strip());
            company.setNombreComercial(request.nombreComercial() == null ? null : request.nombreComercial().strip());
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
            return view(company);
        }, view -> view);
    }

    @Operation(operationId = "listCompanies")
    @GET
    @Transactional
    public Page<CompanyView> list(
            @PathParam("tenantId") UUID tenantId, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = Page.limit(limit);
        int from = Page.offset(offset);
        List<CompanyModel> companies = companyProvider.getCompanies(from, size);
        return new Page<>(companies.stream().map(CompanyResource::view).toList(), size, from);
    }

    @Operation(operationId = "getCompany")
    @GET
    @Path("/{companyId}")
    @Transactional
    public CompanyView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        return view(companyManager.getCompany(companyId));
    }

    @Operation(operationId = "listSeries")
    @GET
    @Path("/{companyId}/series")
    @Transactional
    public List<SeriesView> series(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        CompanyModel company = companyManager.getCompany(companyId);
        List<SeriesModel> series = seriesProvider.getSeries(company.getId(), company.getSunatEnv());
        return series.stream().map(CompanyResource::view).toList();
    }

    @APIResponse(responseCode = "201", description = "Serie creada (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SeriesView.class)))
    @APIResponse(responseCode = "409", description = "La serie ya existe, o Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createSeries")
    @POST
    @Path("/{companyId}/series")
    public Response createSeries(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid CreateSeries request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("series", companyId, request), () -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            CompanyModel company = companyManager.getCompany(companyId);
            SeriesModel series = seriesProvider.newSeries(Ids.newId());
            series.setTenantId(tenantId);
            series.setCompanyId(company.getId());
            series.setSunatEnv(company.getSunatEnv());
            series.setTipoComprobante(request.tipoComprobante());
            series.setSerie(request.serie());
            // Onboarding from another provider: continue its numbering (DESIGN.md §9.2.1, 1033)
            series.setNextNumber(request.nextNumber() == null ? 1 : request.nextNumber());
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
            return view(series);
        }, view -> view);
    }

    /**
     * Moves a series' next number forward or (de)activates it (DESIGN.md §8, §10.8). Each change is
     * a compare-and-set on the series row, which an emit holds locked until it commits: a number is
     * never handed out twice, and once deactivation commits no emit can take the series.
     */
    @APIResponse(responseCode = "200", description = "La serie actualizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SeriesView.class)))
    @APIResponse(responseCode = "409", description = "El siguiente número retrocedería por debajo de los ya usados.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "updateSeries", summary = "Cambia el siguiente número de una serie, o la activa o desactiva")
    @PUT
    @Path("/{companyId}/series/{seriesId}")
    @Transactional
    public SeriesView updateSeries(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("seriesId") UUID seriesId, @Valid UpdateSeries request) {
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
        return view(current(seriesId));
    }

    /** The series row as committed so far in this transaction (the native updates bypass the session). */
    private SeriesModel current(UUID seriesId) {
        SeriesModel series = seriesProvider.getSeries(seriesId).orElse(null);
        seriesProvider.refresh(series);
        return series;
    }


    static CompanyView view(CompanyModel c) {
        return new CompanyView(c.getId(), c.getRuc(), c.getRazonSocial(), c.getNombreComercial(), c.getSunatEnv(), c.getEmissionMode(), c.getRucVerifiedAt(), c.getEmail(),
                c.isDeliveredOnEmitDefault(), c.isSendingPaused(), c.getDireccion(), c.getPdfFormat(), c.isEmailOnAccept(), c.getLogoRef() != null, c.getSubmissionRoute(),
                c.getLibroReclamacionesUrl(), c.getEmailSubject(), c.getEmailBody());
    }

    static SeriesView view(SeriesModel s) {
        return new SeriesView(s.getId(), s.getSunatEnv(), s.getTipoComprobante(), s.getSerie(), s.getNextNumber(), s.isDefault(), s.isActive());
    }
}
