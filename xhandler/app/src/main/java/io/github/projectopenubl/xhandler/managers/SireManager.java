package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.CompanyProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineException;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.utils.RegisterFormats;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.SireProvider;
import jakarta.transaction.Transactional;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * SIRE for one company register and period (DESIGN.md §11.7): request SUNAT's proposal, reconcile
 * it with the stored documents and receptions, submit the replacement, follow the tickets. SUNAT is
 * called with no transaction open (§10.8).
 *
 * <p>States: requested (waiting for the proposal's ticket) → proposal_ready (reconciled) →
 * submitting → submitted (waiting for the replacement's ticket) → accepted; error, credentials
 * (SUNAT refused the API credentials; resumes when they are updated) and unknown (a replacement
 * may have reached SUNAT: never resent without the user confirming it didn't).
 *
 * <p>Concurrency: a proposal request is claimed on the row before SUNAT is called, so only one
 * caller asks; every later change is a compare-and-set on the state and the ticket it acted on.
 * Failed polls back off exponentially and give up after {@link #MAX_ATTEMPTS}.
 */
@ApplicationScoped
public class SireManager {

    private final CompanyProvider companyProvider;

    static final int MAX_ATTEMPTS = 20;
    private static final String CLAIM = "claim:";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(SireManager.class);

    private final SireProvider sireProvider;
    private final TenantAccess access;
    private final Engine engine;
    private final CredentialManager credentials;
    private final FileStore files;
    private final RegisterManager registers;
    private final NotificationManager notifications;

    public SireManager(SireProvider sireProvider, TenantAccess access, Engine engine, CredentialManager credentials, FileStore files,
            RegisterManager registers, NotificationManager notifications, CompanyProvider companyProvider) {
        this.companyProvider = companyProvider;
        this.sireProvider = sireProvider;
        this.access = access;
        this.engine = engine;
        this.credentials = credentials;
        this.files = files;
        this.registers = registers;
        this.notifications = notifications;
    }

    /** Production, or the fake; SIRE has no beta. */
    static EngineModels.Environment environment(String sunatEnv) {
        return "fake".equals(sunatEnv) ? EngineModels.Environment.FAKE : EngineModels.Environment.PRODUCTION;
    }

    record Period(UUID id, UUID companyId, String sunatEnv, String book, String period, String status, String proposalTicket,
            String replacementTicket, int attempts) {}

    private Period load(UUID id) {
        SireProvider.Period r = sireProvider.getPeriod(id);
        return new Period(r.id(), r.companyId(), r.sunatEnv(), r.book(), r.period(), r.status(), r.proposalTicket(), r.replacementTicket(),
                r.attempts());
    }

    private Optional<EngineModels.ApiCredentials> apiCredentials(TenantScope scope, CompanyModel company, String purpose) {
        return credentials.read(scope, company, "sire", purpose)
                .map(f -> new EngineModels.ApiCredentials(f.get("client_id"), f.get("client_secret"), f.get("username"), f.get("password")));
    }

    /**
     * Compare-and-set: moves a period from {@code from} (and, when given, the ticket it acted on) to
     * {@code to}, setting {@code columns}. False if someone else moved it first.
     */
    private boolean transition(UUID tenantId, UUID id, String from, String ticketColumn, String ticket, String to, Map<String, Object> columns) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            return sireProvider.transitionPeriod(id, from, ticketColumn, ticket, to, columns) == 1;
        });
    }

    /** A failed exchange: retried later, with backoff; after too many, the period is in error. */
    private void backoff(UUID tenantId, Period p, String ticketColumn, String ticket, Map<String, Object> extra) {
        int attempts = p.attempts() + 1;
        Map<String, Object> columns = new LinkedHashMap<>(extra);
        columns.put("poll_attempts", attempts);
        if (attempts >= MAX_ATTEMPTS && !"submitted".equals(p.status())) {
            columns.put("detail", "SUNAT no respondió después de varios intentos. Solicite la propuesta de nuevo.");
            transition(tenantId, p.id(), p.status(), ticketColumn, ticket, "error", columns);
            return;
        }
        long minutes = Math.min(360, 1L << Math.min(attempts, 9));
        columns.put("next_poll_at", minutes + " minutes");
        transition(tenantId, p.id(), p.status(), ticketColumn, ticket, p.status(), columns);
    }

    private static Map<String, Object> progressed(Map<String, Object> columns) {
        Map<String, Object> all = new LinkedHashMap<>(columns);
        all.put("poll_attempts", 0);
        all.put("next_poll_at", "0 minutes");
        return all;
    }

    private void notify(UUID tenantId, String kind, String title, String body) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            notifications.publish(tenantId, kind, title, body, null);
        });
    }

    /** After the company's SIRE credentials change: periods waiting for them resume (in the caller's transaction). */
    @Transactional(Transactional.TxType.MANDATORY)
    public int resumeAfterCredentials(UUID companyId) {
        return sireProvider.resumeAfterCredentials(companyId);
    }

    /** Asks SUNAT for the proposal of a period in {@code requested} without a ticket; one caller at a time. */
    public void requestProposal(UUID tenantId, UUID id) {
        String claim = CLAIM + UUID.randomUUID();
        record Call(Period period, CompanyModel company, EngineModels.ApiCredentials creds) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            int claimed = sireProvider.claimProposal(id, claim);
            if (claimed == 0) {
                return null;
            }
            Period p = load(id);
            CompanyModel company = companyProvider.getCompany(p.companyId()).orElse(null);
            return new Call(p, company, apiCredentials(scope, company, "sire:" + id).orElse(null));
        });
        if (call == null) {
            return;
        }
        Period p = call.period();
        if (call.creds() == null) {
            transition(tenantId, id, "requested", "proposal_ticket", claim, "credentials",
                    Map.of("proposal_ticket", "", "resume_status", "requested", "detail", "Registre las credenciales de la API SIRE de la empresa."));
            return;
        }
        EngineModels.SireTicketResponse response;
        try {
            response = engine.sireProposal(new EngineModels.SireProposalRequest(environment(p.sunatEnv()), call.creds(), p.book(), p.period()));
        } catch (EngineException e) {
            LOG.warnf("SIRE proposal of %s: engine failed: %s", id, e.getMessage());
            backoff(tenantId, p, "proposal_ticket", claim, Map.of("proposal_ticket", "", "detail", "El servicio no respondió; se reintentará."));
            return;
        }
        switch (response.state()) {
            case "ok" -> transition(tenantId, id, "requested", "proposal_ticket", claim, "requested",
                    progressed(Map.of("proposal_ticket", response.ticket(), "detail", "")));
            case "unauthorized" -> {
                if (transition(tenantId, id, "requested", "proposal_ticket", claim, "credentials",
                        Map.of("proposal_ticket", "", "resume_status", "requested", "detail", "SUNAT rechazó las credenciales de la API SIRE."))) {
                    notify(tenantId, "sire.credentials", "SIRE: credenciales rechazadas",
                            "SUNAT rechazó las credenciales de la API SIRE de " + call.company().getRazonSocial() + ". Actualícelas para continuar.");
                }
            }
            default -> {
                LOG.infof("SIRE proposal of %s: SUNAT unavailable: %s", id, response.description());
                backoff(tenantId, p, "proposal_ticket", claim, Map.of("proposal_ticket", "", "detail", "SUNAT no respondió; se reintentará."));
            }
        }
    }

    /** Follows the ticket of a requested proposal or a submitted replacement, and recovers stale submissions. */
    public void poll(UUID tenantId, UUID id) {
        record Call(Period period, CompanyModel company, EngineModels.ApiCredentials creds) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            Period p = load(id);
            CompanyModel company = companyProvider.getCompany(p.companyId()).orElse(null);
            return new Call(p, company, apiCredentials(scope, company, "sire:" + id).orElse(null));
        });
        Period p = call.period();
        if ("submitting".equals(p.status())) {
            // The replacement job died between building and answering: SUNAT may have it
            transition(tenantId, id, "submitting", null, null, "unknown", Map.of("detail",
                    "No sabemos si SUNAT recibió el reemplazo. Verifique en SUNAT Operaciones en Línea antes de enviarlo de nuevo."));
            return;
        }
        if ("requested".equals(p.status()) && (p.proposalTicket() == null || p.proposalTicket().isEmpty()
                || p.proposalTicket().startsWith(CLAIM))) {
            requestProposal(tenantId, id);
            return;
        }
        boolean proposal = "requested".equals(p.status());
        String ticketColumn = proposal ? "proposal_ticket" : "replacement_ticket";
        String ticket = proposal ? p.proposalTicket() : "submitted".equals(p.status()) ? p.replacementTicket() : null;
        if (ticket == null) {
            return;
        }
        if (call.creds() == null) {
            backoff(tenantId, p, ticketColumn, ticket, Map.of("detail", "Registre las credenciales de la API SIRE de la empresa."));
            return;
        }
        EngineModels.SireTicketStatus status;
        try {
            status = engine.sireTicket(new EngineModels.SireTicketRequest(environment(p.sunatEnv()), call.creds(), p.book(), p.period(), ticket));
        } catch (EngineException e) {
            LOG.warnf("SIRE ticket %s of %s: engine failed: %s", ticket, id, e.getMessage());
            backoff(tenantId, p, ticketColumn, ticket, Map.of());
            return;
        }
        if ("unauthorized".equals(status.state())) {
            if (proposal) {
                transition(tenantId, id, "requested", ticketColumn, ticket, "credentials",
                        Map.of("resume_status", "requested", "detail", "SUNAT rechazó las credenciales de la API SIRE."));
            } else {
                // The replacement's ticket must not be lost: keep following it once credentials work again
                backoff(tenantId, p, ticketColumn, ticket, Map.of("detail", "SUNAT rechazó las credenciales de la API SIRE; actualícelas."));
            }
            return;
        }
        if (!"ok".equals(status.state())) {
            backoff(tenantId, p, ticketColumn, ticket, Map.of());
            return;
        }
        if ("pending".equals(status.ticketState())) {
            transition(tenantId, id, p.status(), ticketColumn, ticket, p.status(), progressed(Map.of()));
            return;
        }
        if ("error".equals(status.ticketState())) {
            LOG.infof("SIRE ticket %s of %s ended in error: %s", ticket, id, status.description());
            if (transition(tenantId, id, p.status(), ticketColumn, ticket, "error", Map.of("detail",
                    "SUNAT observó el " + (proposal ? "pedido de propuesta" : "reemplazo") + ". Revise el detalle en SUNAT Operaciones en Línea."))) {
                notify(tenantId, "sire.error", "SIRE: SUNAT observó el registro",
                        "El registro " + p.book().toUpperCase() + " " + p.period() + " de " + call.company().getRazonSocial() + " tiene observaciones de SUNAT.");
            }
            return;
        }
        if (proposal) {
            proposalReady(tenantId, p, ticket, status);
        } else if (transition(tenantId, id, "submitted", ticketColumn, ticket, "accepted", progressed(Map.of("detail", "")))) {
            notify(tenantId, "sire.accepted", "SIRE: registro aceptado",
                    "SUNAT aceptó el registro " + p.book().toUpperCase() + " " + p.period() + " de " + call.company().getRazonSocial() + ".");
        }
    }

    private void proposalReady(UUID tenantId, Period p, String ticket, EngineModels.SireTicketStatus status) {
        byte[] zip;
        List<RegisterFormats.ProposalEntry> proposal;
        try {
            if (status.files() == null || status.files().isEmpty()) {
                throw new IllegalStateException("no proposal file");
            }
            zip = Base64.getDecoder().decode(status.files().get(0).contentBase64());
            // Parsed before stored: a bad file is retried, not kept
            proposal = RegisterFormats.readProposal(p.book(), zip);
        } catch (RuntimeException e) {
            LOG.warnf("SIRE proposal of %s unreadable: %s", p.id(), e.toString());
            backoff(tenantId, p, "proposal_ticket", ticket, Map.of("detail", "No se pudo leer la propuesta de SUNAT; se reintentará."));
            return;
        }
        FileStore.Stored stored = files.put(tenantId, zip);
        String reconciliation = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            List<RegisterManager.Row> rows = "rvie".equals(p.book())
                    ? registers.sales(p.companyId(), p.sunatEnv(), p.period())
                    : registers.purchases(p.companyId(), p.period());
            return JSON.writeValueAsString(RegisterFormats.reconcile(p.book(), proposal, rows));
        });
        transition(tenantId, p.id(), "requested", "proposal_ticket", ticket, "proposal_ready",
                progressed(Map.of("proposal_ref", stored.key(), "reconciliation", reconciliation, "detail", "")));
    }

    /** Builds the replacement from the stored rows and submits it (a period in {@code submitting}). */
    public void replace(UUID tenantId, UUID id) {
        record Call(Period period, CompanyModel company, EngineModels.ApiCredentials creds, List<RegisterManager.Row> rows) {}
        Call call = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            Period p = load(id);
            CompanyModel company = companyProvider.getCompany(p.companyId()).orElse(null);
            List<RegisterManager.Row> rows = "rvie".equals(p.book())
                    ? registers.sales(p.companyId(), p.sunatEnv(), p.period())
                    : registers.purchases(p.companyId(), p.period());
            return new Call(p, company, apiCredentials(scope, company, "sire:" + id).orElse(null), rows);
        });
        Period p = call.period();
        if (!"submitting".equals(p.status())) {
            return;
        }
        if (call.creds() == null) {
            transition(tenantId, id, "submitting", null, null, "credentials",
                    Map.of("resume_status", "proposal_ready", "detail", "Registre las credenciales de la API SIRE de la empresa."));
            return;
        }
        RegisterFormats.File file = RegisterFormats.sire(p.book(), call.company().getRuc(), call.company().getRazonSocial(), p.period(), call.rows());
        FileStore.Stored stored = files.put(tenantId, file.content());
        EngineModels.SireTicketResponse response;
        try {
            response = engine.sireReplace(new EngineModels.SireReplaceRequest(environment(p.sunatEnv()), call.creds(), p.book(), p.period(),
                    file.name(), Base64.getEncoder().encodeToString(file.content())));
        } catch (EngineException e) {
            // The engine may have sent it before failing: unknown, not "try again"
            LOG.warnf("SIRE replacement of %s: engine failed: %s", id, e.getMessage());
            response = new EngineModels.SireTicketResponse("unknown", null, e.getMessage());
        }
        switch (response.state()) {
            case "ok" -> transition(tenantId, id, "submitting", null, null, "submitted",
                    progressed(Map.of("replacement_ticket", response.ticket(), "replacement_ref", stored.key(), "detail", "")));
            case "unauthorized" -> transition(tenantId, id, "submitting", null, null, "credentials",
                    Map.of("resume_status", "proposal_ready", "detail", "SUNAT rechazó las credenciales de la API SIRE."));
            case "unknown" -> {
                LOG.warnf("SIRE replacement of %s: outcome unknown: %s", id, response.description());
                transition(tenantId, id, "submitting", null, null, "unknown", Map.of("replacement_ref", stored.key(), "detail",
                        "No sabemos si SUNAT recibió el reemplazo. Verifique en SUNAT Operaciones en Línea antes de enviarlo de nuevo."));
            }
            default -> {
                LOG.infof("SIRE replacement of %s not taken: %s", id, response.description());
                transition(tenantId, id, "submitting", null, null, "proposal_ready",
                        Map.of("detail", "SUNAT no recibió el reemplazo; intente enviarlo de nuevo."));
            }
        }
    }
}
