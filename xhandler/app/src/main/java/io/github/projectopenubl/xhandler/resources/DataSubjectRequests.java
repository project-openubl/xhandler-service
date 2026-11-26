package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.PrivacyProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.representations.idm.Page;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.managers.NotificationManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Data-subject requests (Ley 29733 and DS 016-2024-JUS; DESIGN.md §16). The tenant is the
 * controller of its buyers' data; the platform is its processor. A request that reaches the
 * platform is registered by a platform admin and forwarded to the tenant at once (the processor's
 * 3-day duty); requests the tenant receives can be tracked here too. The tenant answers within the
 * law's term — 20 days for access, 10 for rectification, cancellation and opposition (calendar
 * days here: a conservative reading) — and gets the personal data the platform holds for that
 * person. Documents themselves can't be erased while tax law requires keeping them (§13).
 */
@Path("/api/v1/tenants/{tenantId}/data-subject-requests")
public class DataSubjectRequests {

    public record NewRequest(
            @NotBlank @Pattern(regexp = "[0-9A-Z]{1,2}") String subjectDocType,
            @NotBlank @Size(max = 20) String subjectDocNumber,
            @Size(max = 200) String subjectName,
            @NotBlank @Pattern(regexp = "access|rectification|cancellation|opposition") String kind,
            @Size(max = 2000) String details) {}

    public record Close(@NotBlank @Size(max = 2000) String resolution) {}

    public record DataSubjectRequestView(
            UUID id,
            String subjectDocType,
            String subjectDocNumber,
            String subjectName,
            @Schema(description = "access, rectification, cancellation u opposition.") String kind,
            @Schema(description = "tenant (registrada por la organización) o platform (llegó a la plataforma).") String channel,
            String details,
            String receivedAt,
            String forwardedAt,
            String responseDueAt,
            @Schema(description = "open o closed.") String status,
            String resolution,
            String closedAt) {}

    /** A document issued to the person (status and amounts, no files). Values as text, as stored. */
    public record PersonalDocument(UUID id, String issuerRuc, String tipoComprobante, String serieNumero, String fechaEmision, String nombre,
            String moneda, String total, String status) {}

    /** A draft naming the person as buyer; deleted ones too, until they are purged. */
    public record PersonalDraft(UUID id, String issuerRuc, String kind, String tipoComprobante, String serie, String nombre, String status,
            String createdAt, String deletedAt) {}

    public record PersonalData(DataSubjectRequestView request, List<PersonalDocument> documents, List<PersonalDraft> drafts) {}

    private final TenantAccess access;
    private final PrivacyProvider privacy;
    private final AuditManager auditor;
    private final NotificationManager notifications;

    public DataSubjectRequests(TenantAccess access, PrivacyProvider privacy, AuditManager auditor, NotificationManager notifications) {
        this.access = access;
        this.privacy = privacy;
        this.auditor = auditor;
        this.notifications = notifications;
    }

    static Duration term(String kind) {
        return "access".equals(kind) ? Duration.ofDays(20) : Duration.ofDays(10);
    }

    /** With the tenant open: records a request and tells the tenant. */
    @Transactional(Transactional.TxType.MANDATORY)
    public UUID register(TenantScope scope, NewRequest request, String channel) {
        UUID id = Ids.newId();
        Instant now = Instant.now();
        privacy.addRequest(id, scope.tenantId(), request.subjectDocType(), request.subjectDocNumber(), request.subjectName(), request.kind(),
                channel, request.details(), now, now.plus(Duration.ofDays(3)), now, now.plus(term(request.kind())), scope.actor().auditName());
        notifications.publish(scope.tenantId(), "privacy.request", "Solicitud de derechos de datos personales",
                "Se registró una solicitud de " + kindName(request.kind()) + " de datos personales. Respóndala antes del "
                        + lima(now.plus(term(request.kind()))) + ".", null);
        auditor.record(scope, "privacy.request_registered", "data_subject_request", id, Map.of("kind", request.kind(), "channel", channel));
        return id;
    }

    public static String lima(Instant at) {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(at.atZone(java.time.ZoneId.of("America/Lima")));
    }

    static String kindName(String kind) {
        return switch (kind) {
            case "access" -> "acceso";
            case "rectification" -> "rectificación";
            case "cancellation" -> "cancelación";
            default -> "oposición";
        };
    }

    @Operation(operationId = "createDataSubjectRequest")
    @POST
    @Transactional
    public DataSubjectRequestView create(@PathParam("tenantId") UUID tenantId, @Valid NewRequest request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        return view(register(scope, request, "tenant"));
    }

    @Operation(operationId = "listDataSubjectRequests", summary = "Solicitudes de derechos de datos personales, las más recientes primero")
    @GET
    @Transactional
    public Page<DataSubjectRequestView> list(@PathParam("tenantId") UUID tenantId,
            @Schema(description = "open o closed; sin filtro, ambas.") @QueryParam("status") String status,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.ADMIN);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        List<UUID> ids = privacy.getRequestIds(status, size, start);
        return new Page<>(ids.stream().map(this::view).toList(), size, start);
    }

    /**
     * What the platform holds about the person: the documents issued to them (status and amounts,
     * no files), and the drafts that name them as buyer, deleted ones too until they are purged.
     */
    @Operation(operationId = "getDataSubjectPersonalData")
    @GET
    @Path("/{id}/personal-data")
    @Transactional
    public PersonalData personalData(@PathParam("tenantId") UUID tenantId, @PathParam("id") UUID id) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        DataSubjectRequestView request = view(id);
        List<PrivacyProvider.PersonalDocumentRow> docs = privacy.getPersonalDocuments(request.subjectDocType(), request.subjectDocNumber());
        List<PrivacyProvider.PersonalDraftRow> drafts = privacy.getPersonalDrafts(request.subjectDocType(), request.subjectDocNumber());
        auditor.record(scope, "privacy.personal_data_read", "data_subject_request", id,
                Map.of("documents", String.valueOf(docs.size()), "drafts", String.valueOf(drafts.size())));
        return new PersonalData(request,
                docs.stream().map(r -> new PersonalDocument(r.id(), r.issuerRuc(), r.tipoComprobante(), r.serie() + "-" + r.numero(),
                        String.valueOf(r.fechaEmision()), String.valueOf(r.nombre()), String.valueOf(r.moneda()), String.valueOf(r.total()),
                        r.status())).toList(),
                drafts.stream().map(r -> new PersonalDraft(r.id(), r.issuerRuc(), r.kind(), r.tipoComprobante(), r.serie(), r.nombre(),
                        r.status(), String.valueOf(r.createdAt()), r.deletedAt() == null ? null : String.valueOf(r.deletedAt()))).toList());
    }

    @Operation(operationId = "closeDataSubjectRequest")
    @POST
    @Path("/{id}/close")
    @Transactional
    public DataSubjectRequestView close(@PathParam("tenantId") UUID tenantId, @PathParam("id") UUID id, @Valid Close request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        int closed = privacy.closeRequest(id, request.resolution(), scope.actor().auditName());
        if (closed == 0) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "privacy.request_closed", "data_subject_request", id, Map.of());
        return view(id);
    }

    DataSubjectRequestView view(UUID id) {
        PrivacyProvider.DataSubjectRequestRow r = privacy.getRequest(id).orElseThrow(ApiException::notFound);
        return new DataSubjectRequestView(r.id(), r.subjectDocType(), r.subjectDocNumber(), r.subjectName(), r.kind(), r.channel(),
                r.details(), String.valueOf(r.receivedAt()), r.forwardedAt() == null ? null : String.valueOf(r.forwardedAt()),
                String.valueOf(r.responseDueAt()), r.status(), r.resolution(),
                r.closedAt() == null ? null : String.valueOf(r.closedAt()));
    }

}
