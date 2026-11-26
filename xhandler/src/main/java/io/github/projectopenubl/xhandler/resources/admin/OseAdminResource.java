package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.clients.OseClients;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.managers.PlatformCredentialManager;
import io.github.projectopenubl.xhandler.models.OseProvider;
import io.github.projectopenubl.xhandler.representations.idm.AssociationRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.BlockedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.NewClientRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.BlockedListImportedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.ImportedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.OseClientActionRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.OseClientCreatedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.OseClientRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.OseForwardingRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.OseRejectedForwardRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.OseReforwardRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.OseUsageRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PlatformCertificateRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.PlatformCredentialRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.admin.SolUserRequestRepresentation;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;
import io.github.projectopenubl.xhandler.utils.Constraints;
import io.github.projectopenubl.xhandler.utils.Ids;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Running the OSE role (DESIGN.md §11.4): its users (issuers or their PSEs), SUNAT's association
 * and blocked lists, the CDR-OSE key and the OSE's SUNAT credentials, and the forwarding queue.
 */
@Extension(name = AdminOpenApi.PROFILE_EXTENSION, value = "")
@Path("/api/admin/v1/ose")
public class OseAdminResource {

    private final PlatformAdmin admin;
    private final OseProvider oseProvider;
    private final PlatformKeys keys;
    private final PlatformCredentialManager credentials;
    private final Optional<String> oseRuc;

    public OseAdminResource(PlatformAdmin admin, OseProvider oseProvider, PlatformKeys keys, PlatformCredentialManager credentials,
            @ConfigProperty(name = "xhandler.ose.ruc") Optional<String> oseRuc) {
        this.oseRuc = oseRuc;
        this.admin = admin;
        this.oseProvider = oseProvider;
        this.keys = keys;
        this.credentials = credentials;
    }

    /** A new OSE user; the password is shown once. */
    @Operation(operationId = "adminCreateOseClient", summary = "Crea un usuario OSE; la contraseña se muestra una sola vez")
    @POST
    @Path("/clients")
    @Transactional
    public OseClientCreatedRepresentation createClient(@Valid NewClientRepresentation request) {
        String who = admin.require();
        UUID id = Ids.newId();
        String username = request.username() != null ? request.username() : request.issuerRuc() + "OSE" + id.toString().substring(0, 4).toUpperCase();
        String password = OseClients.newPassword();
        try {
            oseProvider.addClient(id, request.issuerRuc(), username, OseClients.hash(password), request.tenantId(), who);
        } catch (PersistenceException e) {
            String constraint = Constraints.violated(e).orElse("");
            if (constraint.contains("tenant")) {
                throw ApiException.invalid("invalid_request", "La organización indicada no existe.", null);
            }
            if (!constraint.isEmpty()) {
                throw ApiException.conflict("username_taken", "Ese usuario ya existe.");
            }
            throw e;
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s created OSE user %s for %s", who, username, request.issuerRuc());
        return new OseClientCreatedRepresentation(id, request.issuerRuc(), username, password);
    }

    @Operation(operationId = "adminListOseClients", summary = "Usuarios OSE")
    @GET
    @Path("/clients")
    @Transactional
    public List<OseClientRepresentation> clients() {
        admin.require();
        return oseProvider.getClients().stream()
                .map(r -> new OseClientRepresentation(r.id(), r.issuerRuc(), r.username(), r.tenantId(), r.status(), r.lockedUntil(), r.lastLoginAt()))
                .toList();
    }

    @Operation(operationId = "adminSuspendOseClient", summary = "Suspende un usuario OSE")
    @POST
    @Path("/clients/{id}/suspend")
    @Transactional
    public OseClientActionRepresentation suspendClient(@PathParam("id") UUID id) {
        return clientAction(id, "suspend");
    }

    @Operation(operationId = "adminActivateOseClient", summary = "Reactiva un usuario OSE")
    @POST
    @Path("/clients/{id}/activate")
    @Transactional
    public OseClientActionRepresentation activateClient(@PathParam("id") UUID id) {
        return clientAction(id, "activate");
    }

    @Operation(operationId = "adminResetOseClientPassword", summary = "Genera una nueva contraseña para un usuario OSE; se muestra una sola vez")
    @POST
    @Path("/clients/{id}/reset-password")
    @Transactional
    public OseClientActionRepresentation resetClientPassword(@PathParam("id") UUID id) {
        return clientAction(id, "reset-password");
    }

    private OseClientActionRepresentation clientAction(UUID id, String action) {
        String who = admin.require();
        String password = "reset-password".equals(action) ? OseClients.newPassword() : null;
        int changed = switch (action) {
            case "suspend" -> oseProvider.suspendClient(id);
            case "activate" -> oseProvider.activateClient(id);
            default -> oseProvider.resetClientPassword(id, OseClients.hash(password));
        };
        if (changed == 0) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s: %s on OSE user %s", who, action, id);
        return new OseClientActionRepresentation(id, action, password);
    }

    /** SUNAT's list of issuers associated with the platform as OSE: replaces the stored one. */
    @Operation(operationId = "adminImportOseAssociation", summary = "Reemplaza la lista de contribuyentes asociados al OSE")
    @PUT
    @Path("/association")
    @Transactional
    public ImportedRepresentation association(List<@Valid AssociationRepresentation> entries, @QueryParam("confirm_empty") boolean confirmEmpty) {
        String who = admin.require();
        if (entries == null || entries.isEmpty() && !confirmEmpty) {
            // An empty list refuses every issuer: only on purpose
            throw ApiException.invalid("invalid_request", "Envíe la lista de contribuyentes asociados (o confirme vaciarla).", null);
        }
        oseProvider.deleteAssociations();
        for (AssociationRepresentation a : entries) {
            oseProvider.addAssociation(a.ruc(), a.startsOn(), a.endsOn());
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s imported the OSE association list (%d issuers)", who, entries.size());
        return new ImportedRepresentation(entries.size());
    }

    /** One of SUNAT's blocked-issuer lists (DL 1532): replaces that list. */
    @Operation(operationId = "adminImportOseBlockedList", summary = "Reemplaza una lista de emisores bloqueados")
    @PUT
    @Path("/blocked/{list}")
    @Transactional
    public BlockedListImportedRepresentation blocked(@PathParam("list") String list, List<@Valid BlockedRepresentation> entries) {
        String who = admin.require();
        if (entries == null || !list.matches("[a-z0-9_-]{2,40}")) {
            throw ApiException.invalid("invalid_request", "Envíe la lista y un nombre de lista válido.", null);
        }
        oseProvider.deleteBlockedList(list);
        for (BlockedRepresentation b : entries) {
            oseProvider.addBlockedIssuer(b.ruc(), list, b.listedOn());
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s imported blocked list %s (%d issuers)", who, list, entries.size());
        return new BlockedListImportedRepresentation(list, entries.size());
    }

    @Operation(operationId = "adminGetOseCdrCertificate", summary = "El certificado que firma los CDR-OSE (404 si no hay)")
    @GET
    @Path("/cdr-certificate")
    public PlatformCertificateRepresentation getCdrCertificate() {
        admin.require();
        return keys.active("ose_cdr").map(OseAdminResource::certificate).orElseThrow(ApiException::notFound);
    }

    /** The certificate used only to sign CDR-OSE (RS 117-2017 art. 4 h), registered in SOL for the OSE's RUC. */
    @Operation(operationId = "adminImportOseCdrCertificate", summary = "Importa el certificado que firma los CDR-OSE (.pfx y contraseña)")
    @POST
    @Path("/cdr-certificate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public PlatformCertificateRepresentation cdrCertificate(@RestForm("file") FileUpload file, @RestForm("password") String password)
            throws IOException {
        String who = admin.require();
        if (file == null || password == null) {
            throw ApiException.invalid("invalid_request", "Envíe el archivo .pfx y su contraseña.", null);
        }
        byte[] pkcs12 = Files.readAllBytes(file.uploadedFile());
        // The CDR-OSE certificate must be the OSE's own (SUNAT checks 2824) and in force
        KeyStore store;
        try {
            store = KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(pkcs12), password.toCharArray());
        } catch (GeneralSecurityException | IOException e) {
            throw ApiException.invalid("invalid_certificate", "No se pudo leer el certificado o la contraseña es incorrecta.", null);
        }
        X509Certificate certificate;
        try {
            String alias = Collections.list(store.aliases()).stream().filter(a -> {
                try {
                    return store.isKeyEntry(a);
                } catch (KeyStoreException e) {
                    return false;
                }
            }).findFirst().orElseThrow();
            certificate = (X509Certificate) store.getCertificate(alias);
            certificate.checkValidity();
        } catch (NoSuchElementException | GeneralSecurityException e) {
            throw ApiException.invalid("invalid_certificate", "El certificado no tiene clave privada o no está vigente.", null);
        }
        if (oseRuc.isEmpty() || !certificate.getSubjectX500Principal().getName().contains(oseRuc.get())) {
            throw ApiException.invalid("invalid_certificate", "El certificado no corresponde al RUC del OSE.", null);
        }
        PlatformKeys.Key key = keys.importKey("ose_cdr", pkcs12, password.toCharArray());
        AdminLog.PLATFORM_AUDIT.infof("admin %s imported the OSE CDR certificate %s", who, key.subject());
        return certificate(key);
    }

    /** The OSE's own SUNAT user for forwarding. */
    @Operation(operationId = "adminSetOseSunatCredentials", summary = "Cambia el usuario SOL del OSE para reenviar a SUNAT")
    @POST
    @Path("/sunat-credentials")
    @Transactional
    public PlatformCredentialRepresentation sunatCredentials(SolUserRequestRepresentation body) {
        String who = admin.require();
        String username = body == null ? null : body.username();
        String password = body == null ? null : body.password();
        if (username == null || password == null) {
            throw ApiException.invalid("invalid_request", "Indique username y password.", null);
        }
        for (PlatformCredentialManager.Entry existing : credentials.list("ose_sunat")) {
            credentials.setActive(existing.id(), false);
        }
        PlatformCredentialManager.Entry entry = credentials.add("ose_sunat", username, Map.of("username", username, "password", password));
        AdminLog.PLATFORM_AUDIT.infof("admin %s set the OSE SUNAT user %s", who, username);
        return credential(entry);
    }

    /** The forwarding queue against the 1-hour limit. */
    @Operation(operationId = "adminGetOseForwarding", summary = "Cola de reenvío a SUNAT frente al límite de 1 hora")
    @GET
    @Path("/forwarding")
    @Transactional
    public OseForwardingRepresentation forwarding() {
        admin.require();
        OseProvider.ForwardingStats r = oseProvider.getForwardingStats();
        List<OseRejectedForwardRepresentation> rejected = oseProvider.getRejectedForwards().stream()
                .map(x -> new OseRejectedForwardRepresentation(x.id(), x.name(), x.lastError(), x.cdrIssuedAt()))
                .toList();
        return new OseForwardingRepresentation(r.pending(), r.oldestPendingSeconds(), r.rejected(), oseProvider.countBlockedForwards(), r.late(),
                r.forwarded(), rejected);
    }

    /** Validations per OSE user and month: the separate billing line (RS 242-2018 art. 7.16), also for issuers that aren't tenants. */
    @Operation(operationId = "adminGetOseUsage", summary = "Validaciones por usuario OSE en un mes (AAAA-MM; el actual por defecto)")
    @GET
    @Path("/usage")
    @Transactional
    public List<OseUsageRepresentation> usage(@QueryParam("month") String month) {
        admin.require();
        YearMonth m;
        try {
            m = month == null ? YearMonth.now() : YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.invalid("invalid_request", "El mes debe ser AAAA-MM.", null);
        }
        return oseProvider.getUsage(m.atDay(1), m.atEndOfMonth()).stream()
                .map(x -> new OseUsageRepresentation(x.clientId(), x.issuerRuc(), x.username(), x.tenantId(), x.validations()))
                .toList();
    }

    /** Sends a rejected forward again (after fixing what SUNAT refused). */
    @Operation(operationId = "adminReforwardOseSubmission", summary = "Reenvía a SUNAT un envío rechazado")
    @POST
    @Path("/submissions/{id}/reforward")
    @Transactional
    public OseReforwardRepresentation reforward(@PathParam("id") UUID id) {
        String who = admin.require();
        int changed = oseProvider.markReforward(id);
        if (changed == 0) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s re-forwarded OSE submission %s", who, id);
        return new OseReforwardRepresentation(id, "pending");
    }

    static PlatformCertificateRepresentation certificate(PlatformKeys.Key key) {
        return new PlatformCertificateRepresentation(key.subject(), key.ruc(), key.validUntil());
    }

    static PlatformCredentialRepresentation credential(PlatformCredentialManager.Entry entry) {
        return new PlatformCredentialRepresentation(entry.id(), entry.purpose(), entry.label(), entry.active());
    }
}
