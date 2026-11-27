package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.jobs.handlers.PseReportJob;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.managers.PlatformCredentialManager;
import io.github.projectopenubl.xhandler.models.PsePlatformProvider;
import io.github.projectopenubl.xhandler.representations.idm.PseRejectedRateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PendingPseAuthorizationRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PlatformCertificateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PlatformCredentialRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PlatformCredentialStateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PseAuthorizationApprovedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.SolUserRequestRepresentation;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;
import io.github.projectopenubl.xhandler.utils.Deadlines;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * The platform as PSE (DESIGN.md §3.2, §21 P7): its certificate, its pool of SOL users, and the
 * rejected-CDR rate SUNAT requires (≤ 10% in the first year, then ≤ 5%, measured per half-year).
 */
@Extension(name = AdminOpenApi.PROFILE_EXTENSION, value = "")
@Path("/api/admin/v1/pse")
public class PseAdminResource {

    private final PlatformAdmin admin;
    private final PlatformKeys keys;
    private final PlatformCredentialManager credentials;
    private final PsePlatformProvider pse;
    private final Optional<LocalDate> registeredOn;
    private final FileStore files;

    public PseAdminResource(PlatformAdmin admin, PlatformKeys keys, PlatformCredentialManager credentials, PsePlatformProvider pse,
            @ConfigProperty(name = "xhandler.pse.registered-on") Optional<LocalDate> registeredOn, FileStore files) {
        this.files = files;
        this.admin = admin;
        this.keys = keys;
        this.credentials = credentials;
        this.pse = pse;
        this.registeredOn = registeredOn;
    }

    @Operation(operationId = "adminGetPseCertificate", summary = "El certificado PSE de la plataforma (404 si no hay)")
    @GET
    @Path("/certificate")
    public PlatformCertificateRepresentation getCertificate() {
        admin.require();
        return keys.active("pse").map(PseAdminResource::certificate).orElseThrow(ApiException::notFound);
    }

    @Operation(operationId = "adminImportPseCertificate", summary = "Importa el certificado PSE de la plataforma (.pfx y contraseña)")
    @POST
    @Path("/certificate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public PlatformCertificateRepresentation certificate(@RestForm("file") FileUpload file, @RestForm("password") String password)
            throws IOException {
        String who = admin.require();
        if (file == null || password == null) {
            throw ApiException.invalid("invalid_request", "Envíe el archivo .pfx y su contraseña.", null);
        }
        PlatformKeys.Key key = keys.importKey("pse", Files.readAllBytes(file.uploadedFile()), password.toCharArray());
        AdminLog.PLATFORM_AUDIT.infof("admin %s imported the PSE certificate %s", who, key.subject());
        return certificate(key);
    }

    @Operation(operationId = "adminAddPseSolUser", summary = "Agrega un usuario SOL al grupo PSE de la plataforma")
    @POST
    @Path("/sol-users")
    @Transactional
    public PlatformCredentialRepresentation addSolUser(SolUserRequestRepresentation body) {
        String who = admin.require();
        String username = body == null ? null : body.username();
        String password = body == null ? null : body.password();
        if (username == null || password == null) {
            throw ApiException.invalid("invalid_request", "Indique username y password.", null);
        }
        String label = body.label() == null ? username : body.label();
        PlatformCredentialManager.Entry entry = credentials.add("pse_sol", label, Map.of("username", username, "password", password));
        AdminLog.PLATFORM_AUDIT.infof("admin %s added PSE SOL user %s", who, entry.label());
        return credential(entry);
    }

    @Operation(operationId = "adminListPseSolUsers", summary = "Usuarios SOL del grupo PSE")
    @GET
    @Path("/sol-users")
    @Transactional
    public List<PlatformCredentialRepresentation> solUsers() {
        admin.require();
        return credentials.list("pse_sol").stream().map(PseAdminResource::credential).toList();
    }

    @Operation(operationId = "adminDeactivatePseSolUser", summary = "Deja de usar un usuario SOL del grupo PSE")
    @POST
    @Path("/sol-users/{id}/deactivate")
    @Transactional
    public PlatformCredentialStateRepresentation deactivate(@PathParam("id") UUID id) {
        String who = admin.require();
        if (!credentials.setActive(id, false)) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s deactivated PSE SOL user %s", who, id);
        return new PlatformCredentialStateRepresentation(id, false);
    }

    @Operation(operationId = "adminActivatePseSolUser", summary = "Vuelve a usar un usuario SOL del grupo PSE")
    @POST
    @Path("/sol-users/{id}/activate")
    @Transactional
    public PlatformCredentialStateRepresentation activate(@PathParam("id") UUID id) {
        String who = admin.require();
        if (!credentials.setActive(id, true)) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s activated PSE SOL user %s", who, id);
        return new PlatformCredentialStateRepresentation(id, true);
    }

    /** Authorizations registered by tenants, waiting for a platform admin to check the evidence. */
    @Operation(operationId = "adminListPendingPseAuthorizations", summary = "Autorizaciones PSE por revisar")
    @GET
    @Path("/authorizations/pending")
    @Transactional
    public List<PendingPseAuthorizationRepresentation> pending() {
        admin.require();
        return pse.getPendingAuthorizations().stream()
                .map(r -> new PendingPseAuthorizationRepresentation(r.id(), r.tenantId(), r.companyId(), r.ruc(), r.razonSocial(), r.authorizedAt(),
                        r.evidence()))
                .toList();
    }

    @Operation(operationId = "adminDownloadPseAuthorizationEvidence", summary = "La evidencia de una autorización PSE (PDF o imagen)")
    @APIResponse(responseCode = "200", description = "El archivo de evidencia.",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @GET
    @Path("/authorizations/{id}/evidence")
    @Transactional
    public Response evidence(@PathParam("id") UUID id) {
        String who = admin.require();
        String ref = pse.getAuthorizationEvidenceRef(id).orElseThrow(ApiException::notFound);
        AdminLog.PLATFORM_AUDIT.infof("admin %s read the evidence of PSE authorization %s", who, id);
        return Response.ok(files.get(ref)).type("application/octet-stream").build();
    }

    @Operation(operationId = "adminApprovePseAuthorization", summary = "Aprueba una autorización PSE")
    @POST
    @Path("/authorizations/{id}/approve")
    @Transactional
    public PseAuthorizationApprovedRepresentation approve(@PathParam("id") UUID id) {
        String who = admin.require();
        if (!pse.approveAuthorization(id, who)) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s approved PSE authorization %s", who, id);
        return new PseAuthorizationApprovedRepresentation(id, true);
    }

    /** The rejected-CDR rate of a half-year ({@code 2027-H1}); the current one by default. */
    @Operation(operationId = "adminGetPseRejectedRate", summary = "Tasa de CDR rechazados de un semestre (AAAA-H1 o AAAA-H2)")
    @GET
    @Path("/rejected-rate")
    @Transactional
    public PseRejectedRateRepresentation rejectedRate(@QueryParam("period") String period) {
        admin.require();
        return rate(pse, period == null ? PseReportJob.periodOf(Deadlines.todayInLima()) : period, registeredOn);
    }

    public static PseRejectedRateRepresentation rate(PsePlatformProvider pse, String period, Optional<LocalDate> registeredOn) {
        if (!period.matches("\\d{4}-H[12]")) {
            throw ApiException.invalid("invalid_request", "El periodo debe ser AAAA-H1 o AAAA-H2.", null);
        }
        int year = Integer.parseInt(period.substring(0, 4));
        LocalDate from = period.endsWith("H1") ? LocalDate.of(year, Month.JANUARY, 1) : LocalDate.of(year, Month.JULY, 1);
        LocalDate to = from.plusMonths(6);
        PsePlatformProvider.Outcomes r = pse.getOutcomes(from.atStartOfDay(Deadlines.LIMA).toInstant(), to.atStartOfDay(Deadlines.LIMA).toInstant());
        long accepted = r.accepted();
        long rejected = r.rejected();
        BigDecimal rate = accepted + rejected == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(rejected).divide(BigDecimal.valueOf(accepted + rejected), 4, RoundingMode.HALF_UP);
        // RS 199-2015 art. 7: 10% during the first year as PSE, 5% afterwards; without the registration
        // date configured, the stricter limit applies (fail closed)
        boolean firstYear = registeredOn.map(d -> from.isBefore(d.plusYears(1))).orElse(false);
        BigDecimal limit = firstYear ? new BigDecimal("0.10") : new BigDecimal("0.05");
        return new PseRejectedRateRepresentation(period, accepted, rejected, rate, limit, rate.compareTo(limit) > 0);
    }

    static PlatformCertificateRepresentation certificate(PlatformKeys.Key key) {
        return new PlatformCertificateRepresentation(key.subject(), key.ruc(), key.validUntil());
    }

    static PlatformCredentialRepresentation credential(PlatformCredentialManager.Entry entry) {
        return new PlatformCredentialRepresentation(entry.id(), entry.purpose(), entry.label(), entry.active());
    }
}
