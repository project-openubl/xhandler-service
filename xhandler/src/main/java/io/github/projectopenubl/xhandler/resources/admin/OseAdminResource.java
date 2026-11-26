package io.github.projectopenubl.xhandler.resources.admin;

import io.github.projectopenubl.xhandler.representations.idm.NewClientRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.BlockedRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.AssociationRepresentation;
import io.github.projectopenubl.xhandler.security.PlatformAdmin;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.clients.OseClients;
import io.github.projectopenubl.xhandler.managers.PlatformCredentialManager;
import io.github.projectopenubl.xhandler.models.OseProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Running the OSE role (DESIGN.md §11.4): its users (issuers or their PSEs), SUNAT's association
 * and blocked lists, the CDR-OSE key and the OSE's SUNAT credentials, and the forwarding queue.
 */
@Path("/api/admin/v1/ose")
public class OseAdminResource {




    private final PlatformAdmin admin;
    private final OseProvider oseProvider;
    private final PlatformKeys keys;
    private final PlatformCredentialManager credentials;
    private final java.util.Optional<String> oseRuc;

    public OseAdminResource(PlatformAdmin admin, OseProvider oseProvider, PlatformKeys keys, PlatformCredentialManager credentials,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.ose.ruc") java.util.Optional<String> oseRuc) {
        this.oseRuc = oseRuc;
        this.admin = admin;
        this.oseProvider = oseProvider;
        this.keys = keys;
        this.credentials = credentials;
    }

    /** A new OSE user; the password is shown once. */
    @POST
    @Path("/clients")
    @Transactional
    public Map<String, Object> createClient(@Valid NewClientRepresentation request) {
        String who = admin.require();
        UUID id = Ids.newId();
        String username = request.username() != null ? request.username() : request.issuerRuc() + "OSE" + id.toString().substring(0, 4).toUpperCase();
        String password = OseClients.newPassword();
        try {
            oseProvider.addClient(id, request.issuerRuc(), username, OseClients.hash(password), request.tenantId(), who);
        } catch (jakarta.persistence.PersistenceException e) {
            String constraint = io.github.projectopenubl.xhandler.utils.Constraints.violated(e).orElse("");
            if (constraint.contains("tenant")) {
                throw ApiException.invalid("invalid_request", "La organización indicada no existe.", null);
            }
            if (!constraint.isEmpty()) {
                throw ApiException.conflict("username_taken", "Ese usuario ya existe.");
            }
            throw e;
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s created OSE user %s for %s", who, username, request.issuerRuc());
        return Map.of("id", id, "issuer_ruc", request.issuerRuc(), "username", username, "password", password);
    }

    @GET
    @Path("/clients")
    @Transactional
    public List<Map<String, Object>> clients() {
        admin.require();
        return oseProvider.getClients().stream().map(r -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", r.id());
            view.put("issuer_ruc", r.issuerRuc());
            view.put("username", r.username());
            view.put("tenant_id", r.tenantId());
            view.put("status", r.status());
            view.put("locked_until", r.lockedUntil() == null ? null : String.valueOf(r.lockedUntil()));
            view.put("last_login_at", r.lastLoginAt() == null ? null : String.valueOf(r.lastLoginAt()));
            return view;
        }).toList();
    }

    @POST
    @Path("/clients/{id}/{action:suspend|activate|reset-password}")
    @Transactional
    public Map<String, Object> clientAction(@PathParam("id") UUID id, @PathParam("action") String action) {
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
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", id);
        answer.put("action", action);
        if (password != null) {
            answer.put("password", password);
        }
        return answer;
    }

    /** SUNAT's list of issuers associated with the platform as OSE: replaces the stored one. */
    @PUT
    @Path("/association")
    @Transactional
    public Map<String, Object> association(List<@Valid AssociationRepresentation> entries,
            @jakarta.ws.rs.QueryParam("confirm_empty") boolean confirmEmpty) {
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
        return Map.of("imported", entries.size());
    }

    /** One of SUNAT's blocked-issuer lists (DL 1532): replaces that list. */
    @PUT
    @Path("/blocked/{list}")
    @Transactional
    public Map<String, Object> blocked(@PathParam("list") String list, List<@Valid BlockedRepresentation> entries) {
        String who = admin.require();
        if (entries == null || !list.matches("[a-z0-9_-]{2,40}")) {
            throw ApiException.invalid("invalid_request", "Envíe la lista y un nombre de lista válido.", null);
        }
        oseProvider.deleteBlockedList(list);
        for (BlockedRepresentation b : entries) {
            oseProvider.addBlockedIssuer(b.ruc(), list, b.listedOn());
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s imported blocked list %s (%d issuers)", who, list, entries.size());
        return Map.of("list", list, "imported", entries.size());
    }

    /** The certificate used only to sign CDR-OSE (RS 117-2017 art. 4 h), registered in SOL for the OSE's RUC. */
    @POST
    @Path("/cdr-certificate")
    public Map<String, Object> cdrCertificate(@RestForm("file") FileUpload file, @RestForm("password") String password) throws IOException {
        String who = admin.require();
        if (file == null || password == null) {
            throw ApiException.invalid("invalid_request", "Envíe el archivo .pfx y su contraseña.", null);
        }
        byte[] pkcs12 = Files.readAllBytes(file.uploadedFile());
        // The CDR-OSE certificate must be the OSE's own (SUNAT checks 2824) and in force
        java.security.KeyStore store;
        try {
            store = java.security.KeyStore.getInstance("PKCS12");
            store.load(new java.io.ByteArrayInputStream(pkcs12), password.toCharArray());
        } catch (java.security.GeneralSecurityException | IOException e) {
            throw ApiException.invalid("invalid_certificate", "No se pudo leer el certificado o la contraseña es incorrecta.", null);
        }
        java.security.cert.X509Certificate certificate;
        try {
            String alias = java.util.Collections.list(store.aliases()).stream().filter(a -> {
                try {
                    return store.isKeyEntry(a);
                } catch (java.security.KeyStoreException e) {
                    return false;
                }
            }).findFirst().orElseThrow();
            certificate = (java.security.cert.X509Certificate) store.getCertificate(alias);
            certificate.checkValidity();
        } catch (java.util.NoSuchElementException | java.security.GeneralSecurityException e) {
            throw ApiException.invalid("invalid_certificate", "El certificado no tiene clave privada o no está vigente.", null);
        }
        if (oseRuc.isEmpty() || !certificate.getSubjectX500Principal().getName().contains(oseRuc.get())) {
            throw ApiException.invalid("invalid_certificate", "El certificado no corresponde al RUC del OSE.", null);
        }
        PlatformKeys.Key key = keys.importKey("ose_cdr", pkcs12, password.toCharArray());
        AdminLog.PLATFORM_AUDIT.infof("admin %s imported the OSE CDR certificate %s", who, key.subject());
        return Map.of("subject", key.subject(), "ruc", String.valueOf(key.ruc()), "valid_until", key.validUntil().toString());
    }

    /** The OSE's own SUNAT user for forwarding. */
    @POST
    @Path("/sunat-credentials")
    @Transactional
    public PlatformCredentialManager.Entry sunatCredentials(Map<String, String> body) {
        String who = admin.require();
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (username == null || password == null) {
            throw ApiException.invalid("invalid_request", "Indique username y password.", null);
        }
        for (PlatformCredentialManager.Entry existing : credentials.list("ose_sunat")) {
            credentials.setActive(existing.id(), false);
        }
        PlatformCredentialManager.Entry entry = credentials.add("ose_sunat", username, Map.of("username", username, "password", password));
        AdminLog.PLATFORM_AUDIT.infof("admin %s set the OSE SUNAT user %s", who, username);
        return entry;
    }

    /** The forwarding queue against the 1-hour limit. */
    @GET
    @Path("/forwarding")
    @Transactional
    public Map<String, Object> forwarding() {
        admin.require();
        OseProvider.ForwardingStats r = oseProvider.getForwardingStats();
        List<OseProvider.RejectedForward> rejected = oseProvider.getRejectedForwards();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("pending", r.pending());
        view.put("oldest_pending_seconds", r.oldestPendingSeconds());
        view.put("rejected", r.rejected());
        view.put("blocked", oseProvider.countBlockedForwards());
        view.put("late", r.late());
        view.put("forwarded", r.forwarded());
        view.put("rejected_items", rejected.stream().map(x -> Map.of("id", x.id(), "name", x.name(), "error", String.valueOf(x.lastError()),
                "cdr_issued_at", String.valueOf(x.cdrIssuedAt()))).toList());
        return view;
    }

    /** Validations per OSE user and month: the separate billing line (RS 242-2018 art. 7.16), also for issuers that aren't tenants. */
    @GET
    @Path("/usage")
    @Transactional
    public List<Map<String, Object>> usage(@jakarta.ws.rs.QueryParam("month") String month) {
        admin.require();
        java.time.YearMonth m = month == null ? java.time.YearMonth.now() : java.time.YearMonth.parse(month);
        return oseProvider.getUsage(m.atDay(1), m.atEndOfMonth()).stream().map(x -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("ose_client_id", x.clientId());
            view.put("issuer_ruc", x.issuerRuc());
            view.put("username", x.username());
            view.put("tenant_id", x.tenantId());
            view.put("validations", x.validations());
            return view;
        }).toList();
    }

    /** Sends a rejected forward again (after fixing what SUNAT refused). */
    @POST
    @Path("/submissions/{id}/reforward")
    @Transactional
    public Map<String, Object> reforward(@PathParam("id") UUID id) {
        String who = admin.require();
        int changed = oseProvider.markReforward(id);
        if (changed == 0) {
            throw ApiException.notFound();
        }
        AdminLog.PLATFORM_AUDIT.infof("admin %s re-forwarded OSE submission %s", who, id);
        return Map.of("id", id, "forward_status", "pending");
    }
}
