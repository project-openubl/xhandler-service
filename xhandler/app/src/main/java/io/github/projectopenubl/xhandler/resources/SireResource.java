package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.SireProvider;
import io.github.projectopenubl.xhandler.jobs.handlers.SireJobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.Page;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.managers.RegisterManager;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * SIRE per company, register ({@code rvie} sales, {@code rce} purchases) and period (DESIGN.md
 * §11.7): request SUNAT's proposal, see its reconciliation with the stored documents, submit the
 * replacement. SUNAT is called by jobs; these endpoints answer {@code 202}.
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/sire")
public class SireResource {

    private final CompanyManager companyManager;

    public record SirePeriodView(
            UUID id, String book, String period, String sunatEnv, String status, String detail, JsonNode reconciliation,
            boolean proposalFile, boolean replacementFile, Instant createdAt, Instant updatedAt) {}

    /**
     * {@code acknowledgeDifferences}: submit although the stored rows don't match SUNAT's proposal
     * (what SUNAT has and the platform doesn't is left out). {@code confirmNotReceived}: after an
     * unknown outcome, the user checked in SOL that SUNAT didn't get the earlier replacement.
     */
    /** A period in a list: its state, without the reconciliation (read it with {@code getSirePeriod}). */
    public record SirePeriodSummary(
            UUID id, String book, String period, String sunatEnv, String status, String detail, boolean proposalFile,
            boolean replacementFile, Instant createdAt, Instant updatedAt) {}

    public record ReplaceRequest(Boolean acknowledgeDifferences, Boolean confirmNotReceived) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TenantAccess access;
    private final Jobs jobs;
    private final SireProvider sireProvider;
    private final FileStore files;
    private final AuditManager auditor;
    private final RegisterManager registers;

    public SireResource(TenantAccess access, Jobs jobs, SireProvider sireProvider, FileStore files, AuditManager auditor, RegisterManager registers,
            CompanyManager companyManager) {
        this.companyManager = companyManager;
        this.registers = registers;
        this.access = access;
        this.jobs = jobs;
        this.sireProvider = sireProvider;
        this.files = files;
        this.auditor = auditor;
    }

    private static void check(String book, String period) {
        if (!"rvie".equals(book) && !"rce".equals(book)) {
            throw ApiException.notFound();
        }
        RegisterManager.month(period);
    }

    /** Asks SUNAT for its proposal (again, to start over after an error or a change). */
    @APIResponse(responseCode = "202", description = "Propuesta solicitada a SUNAT; la trae un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SirePeriodView.class)))
    @Operation(operationId = "requestSireProposal")
    @POST
    @Path("/{book}/{period}/proposal")
    @Transactional
    public Response requestProposal(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("book") String book, @PathParam("period") String period) {
        check(book, period);
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        CompanyModel company = companyManager.getCompany(companyId);
        if ("beta".equals(company.getSunatEnv())) {
            throw ApiException.conflict("sire_not_available", "SUNAT no tiene ambiente de pruebas para SIRE.");
        }
        int created = sireProvider.addPeriod(Ids.newId(), tenantId, companyId, company.getSunatEnv(), book, period, scope.actor().auditName());
        SireProvider.PeriodState existing = sireProvider.getPeriodStateForUpdate(companyId, company.getSunatEnv(), book, period);
        UUID id = existing.id();
        if (created == 0) {
            String status = existing.status();
            if ("submitting".equals(status) || "submitted".equals(status)) {
                throw ApiException.conflict("sire_in_progress", "El reemplazo está en proceso en SUNAT; espere su resultado.");
            }
            if ("unknown".equals(status)) {
                throw ApiException.conflict("sire_unknown",
                        "No sabemos si SUNAT recibió el último reemplazo. Verifíquelo y confirme antes de continuar.");
            }
            if ("accepted".equals(status) || "proposal_ready".equals(status)) {
                // Starting over discards SUNAT's acceptance or an admin's review
                if (!scope.role().allows(Role.ADMIN)) {
                    throw ApiException.forbidden();
                }
            }
            sireProvider.restartPeriod(id, scope.actor().auditName());
        }
        jobs.enqueue(scope, SireJobs.Request.NAME, 3, Map.of("sire_period_id", id.toString()), List.of("sire:" + id), null);
        auditor.record(scope, "sire.proposal_requested", "company", companyId, Map.of("book", book, "period", period));
        return Response.status(202).entity(view(id)).build();
    }

    /** Submits the stored rows as the replacement of SUNAT's proposal. */
    @APIResponse(responseCode = "202", description = "Reemplazo enviado a SUNAT; lo sigue un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SirePeriodView.class)))
    @Operation(operationId = "replaceSireProposal")
    @POST
    @Path("/{book}/{period}/replace")
    @Transactional
    public Response replace(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("book") String book,
            @PathParam("period") String period, ReplaceRequest request) {
        check(book, period);
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        CompanyModel company = companyManager.getCompany(companyId);
        UUID id = find(companyId, company.getSunatEnv(), book, period);
        boolean acknowledge = request != null && Boolean.TRUE.equals(request.acknowledgeDifferences());
        boolean confirmed = request != null && Boolean.TRUE.equals(request.confirmNotReceived());
        SireProvider.ReplaceState row = sireProvider.getReplaceStateForUpdate(id);
        String status = row.status();
        if ("unknown".equals(status) && !confirmed) {
            throw ApiException.conflict("sire_unknown",
                    "No sabemos si SUNAT recibió el último reemplazo. Verifique en SUNAT Operaciones en Línea y confirme que no lo recibió.");
        }
        if (row.reconciliation() == null || !List.of("proposal_ready", "error", "unknown").contains(status)) {
            throw ApiException.conflict("sire_proposal_missing", "Solicite primero la propuesta de SUNAT y revise la conciliación.");
        }
        boolean matches;
        try {
            matches = JSON.readTree(row.reconciliation()).path("matches").asBoolean(false);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        if (!matches && !acknowledge) {
            // The replacement is built from the stored rows only: what SUNAT has and the platform doesn't would be dropped
            throw ApiException.conflict("sire_differences",
                    "La propuesta de SUNAT no coincide con los comprobantes registrados. Revise la conciliación y confirme las diferencias.");
        }
        long unresolved = "rvie".equals(book) ? registers.unresolved(companyId, company.getSunatEnv(), period) : 0;
        if (unresolved > 0) {
            throw ApiException.conflict("sire_unresolved_documents",
                    "Hay " + unresolved + " comprobantes del periodo sin respuesta definitiva de SUNAT. Espere a que se resuelvan.");
        }
        List<RegisterManager.Row> rows = "rvie".equals(book) ? registers.sales(companyId, company.getSunatEnv(), period) : registers.purchases(companyId, period);
        long missing = rows.stream().filter(RegisterManager.Row::amountsMissing).count();
        if (missing > 0) {
            throw ApiException.conflict("amounts_pending",
                    missing + " comprobantes aún no tienen el desglose de impuestos; se completa en unos minutos.");
        }
        sireProvider.markSubmitting(id);
        jobs.enqueue(scope, SireJobs.Replace.NAME, 3, Map.of("sire_period_id", id.toString()), List.of("sire:" + id), null);
        auditor.record(scope, "sire.replacement_submitted", "company", companyId, Map.of("book", book, "period", period,
                "acknowledged_differences", String.valueOf(!matches), "confirmed_not_received", String.valueOf(confirmed)));
        return Response.status(202).entity(view(id)).build();
    }

    @Operation(operationId = "getSirePeriod")
    @GET
    @Path("/{book}/{period}")
    @Transactional
    public SirePeriodView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("book") String book,
            @PathParam("period") String period) {
        check(book, period);
        access.enter(tenantId, Role.VIEWER);
        CompanyModel company = companyManager.getCompany(companyId);
        return view(find(companyId, company.getSunatEnv(), book, period));
    }

    /** The company's SIRE periods in its current environment, the latest period first. */
    @Operation(operationId = "listSirePeriods")
    @GET
    @Transactional
    public Page<SirePeriodSummary> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Schema(description = "rvie (ventas) o rce (compras); sin filtro, ambos.") @QueryParam("book") String book,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        if (book != null && !"rvie".equals(book) && !"rce".equals(book)) {
            throw ApiException.invalid("invalid_request", "book debe ser rvie o rce.", "book");
        }
        access.enter(tenantId, Role.VIEWER);
        CompanyModel company = companyManager.getCompany(companyId);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        return new Page<>(sireProvider.getPeriods(companyId, company.getSunatEnv(), book, size, start).stream()
                .map(r -> new SirePeriodSummary(r.id(), r.book(), r.period(), r.sunatEnv(), r.status(), r.detail(), r.proposalFile(),
                        r.replacementFile(), r.createdAt(), r.updatedAt())).toList(), size, start);
    }

    /** SUNAT's proposal ({@code proposal}) or the submitted replacement ({@code replacement}), as SUNAT's ZIP. */
    @APIResponse(responseCode = "200", description = "El archivo de SUNAT (ZIP).", content = {
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "downloadSireFile")
    @GET
    @Path("/{book}/{period}/files/{kind}")
    @Transactional
    public Response file(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("book") String book,
            @PathParam("period") String period, @PathParam("kind") String kind) {
        check(book, period);
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        CompanyModel company = companyManager.getCompany(companyId);
        UUID id = find(companyId, company.getSunatEnv(), book, period);
        Optional<String> ref = switch (kind) {
            case "proposal" -> sireProvider.getProposalRef(id);
            case "replacement" -> sireProvider.getReplacementRef(id);
            default -> throw ApiException.notFound();
        };
        if (ref.isEmpty()) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "sire.file_read", "company", companyId, Map.of("book", book, "period", period, "kind", kind));
        return Response.ok(files.get(ref.get())).type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"sire-" + book + "-" + period + "-" + kind + ".zip\"").build();
    }

    private UUID find(UUID companyId, String sunatEnv, String book, String period) {
        return sireProvider.getPeriodId(companyId, sunatEnv, book, period).orElseThrow(ApiException::notFound);
    }

    private SirePeriodView view(UUID id) {
        SireProvider.PeriodView r = sireProvider.getPeriodView(id);
        JsonNode reconciliation;
        try {
            reconciliation = r.reconciliation() == null ? null : JSON.readTree(r.reconciliation());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new SirePeriodView(r.id(), r.book(), r.period(), r.sunatEnv(), r.status(), r.detail(), reconciliation,
                r.proposalFile(), r.replacementFile(), r.createdAt(), r.updatedAt());
    }
}
