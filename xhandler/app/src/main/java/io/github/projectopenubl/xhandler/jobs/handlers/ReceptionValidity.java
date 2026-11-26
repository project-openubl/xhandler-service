package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineException;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.managers.CredentialManager;
import io.github.projectopenubl.xhandler.managers.PlatformCredentialManager;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.ReceptionProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks a received document with SUNAT's validity service (validaCDPcriterios, DESIGN.md §11.5):
 * with the company's SOL user in production, or the platform's validity credential. The SUNAT call
 * runs with no transaction open (§10.8). Runs at reception and as a job for unknown states.
 */
@ApplicationScoped
public class ReceptionValidity implements JobHandler {

    private final CompanyProvider companyProvider;

    private final ReceptionProvider receptionProvider;
    private final TenantAccess access;
    private final Engine engine;
    private final CredentialManager credentials;
    private final PlatformCredentialManager platformCredentials;

    public ReceptionValidity(ReceptionProvider receptionProvider, TenantAccess access, Engine engine, CredentialManager credentials,
            PlatformCredentialManager platformCredentials, CompanyProvider companyProvider) {
        this.companyProvider = companyProvider;
        this.receptionProvider = receptionProvider;
        this.access = access;
        this.engine = engine;
        this.credentials = credentials;
        this.platformCredentials = platformCredentials;
    }

    @Override
    public String name() {
        return "receptions.validity";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        List<ReceptionProvider.DueReception> due = QuarkusTransaction.requiringNew().call(receptionProvider::getReceptionsToCheck);
        int checked = 0;
        for (ReceptionProvider.DueReception d : due) {
            try {
                if (check(d.tenantId(), d.receptionId())) {
                    checked++;
                }
            } catch (RuntimeException e) {
                // One reception's failure doesn't stop the rest
                org.jboss.logging.Logger.getLogger(ReceptionValidity.class).warnf("validity check of reception %s failed: %s", d.receptionId(), e.toString());
                stamp(d.tenantId(), d.receptionId(), null);
            }
        }
        return Map.of("checked", checked, "due", due.size());
    }

    /** True when SUNAT answered definitively (valid, not found, voided, rejected). */
    public boolean check(UUID tenantId, UUID receptionId) {
        record Request(EngineModels.ValidityRequest request) {}
        Request request = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enterAsPlatform(tenantId);
            ReceptionProvider.ValidityRow r = receptionProvider.getValidityRow(receptionId);
            CompanyModel company = companyProvider.getCompany(r.companyId()).orElse(null);
            if (r.fechaEmision() == null || r.total() == null) {
                return null;
            }
            // The validity service is production SUNAT (the fake stands in for it in development): a
            // company's SOL user only works where it is real; beta companies use the platform's credential
            EngineModels.Environment environment = "fake".equals(company.getSunatEnv()) ? EngineModels.Environment.FAKE
                    : EngineModels.Environment.PRODUCTION;
            Optional<EngineModels.Credentials> sol = !"beta".equals(company.getSunatEnv())
                    ? credentials.read(scope, company, "sol", "reception-validity:" + receptionId)
                            .map(f -> new EngineModels.Credentials(f.get("username"), f.get("password"), null, null))
                    : Optional.empty();
            EngineModels.Credentials creds = sol.orElseGet(() -> platformCredentials.pick("validity")
                    .map(id -> platformCredentials.read(id, "validity", "reception-validity:" + receptionId))
                    .map(f -> new EngineModels.Credentials(f.get("username"), f.get("password"), null, null))
                    .orElse(null));
            if (creds == null) {
                return null;
            }
            return new Request(new EngineModels.ValidityRequest(environment, creds,
                    new EngineModels.DocumentKey(r.supplierRuc(), r.tipoComprobante(), r.serie(), r.numero()), "6", company.getRuc(),
                    r.fechaEmision().toString(), r.total().toPlainString()));
        });
        if (request == null) {
            // Can't be checked now (no credential, no date or total): stamped, so it waits its turn
            stamp(tenantId, receptionId, null);
            return false;
        }
        String state;
        try {
            state = engine.validity(request.request()).state();
        } catch (EngineException | jakarta.ws.rs.ProcessingException e) {
            state = "unknown";
        }
        if ("not_delivered".equals(state)) {
            state = "unknown";
        }
        stamp(tenantId, receptionId, state);
        return !"unknown".equals(state);
    }

    /** Records a check; a null state keeps the current one (the check couldn't be made). */
    private void stamp(UUID tenantId, UUID receptionId, String state) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            receptionProvider.stampValidity(receptionId, state);
        });
    }
}
