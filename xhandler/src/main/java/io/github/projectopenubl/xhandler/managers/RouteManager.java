package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.CredentialProvider;
import io.github.projectopenubl.xhandler.models.DocumentModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where and with which credentials a document is sent (DESIGN.md §3.2, §11.2), decided when the
 * document is created. Every document goes to SUNAT directly; the endpoint id ({@code
 * <env>:<service>}) groups documents for the reconciler's breakers and caps.
 */
@ApplicationScoped
public class RouteManager {

    private final CredentialProvider credentialProvider;

    /** Where to send, and as whom. Null credentials: the environment's shared user (beta, fake). */
    public record Target(EngineModels.Environment environment, EngineModels.Credentials credentials) {}

    /** The endpoint and credential a new document is sent with. */
    public record Assignment(String endpoint, UUID credentialId) {}

    private final CredentialManager credentials;
    private final io.github.projectopenubl.xhandler.managers.PlatformCredentialManager platformCredentials;

    private final io.github.projectopenubl.xhandler.managers.AuditManager auditor;

    public RouteManager(CredentialManager credentials, io.github.projectopenubl.xhandler.managers.PlatformCredentialManager platformCredentials,
            io.github.projectopenubl.xhandler.managers.AuditManager auditor, CredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
        this.auditor = auditor;
        this.credentials = credentials;
        this.platformCredentials = platformCredentials;
    }

    /** SUNAT service of a document type: billService (invoice), otroscpe (perception) or GRE (despatch). */
    public static String service(String tipoComprobante) {
        return switch (tipoComprobante) {
            case "20", "40", "RR" -> "perception";
            case "09", "31" -> "despatch";
            default -> "invoice";
        };
    }

    public static String endpoint(String sunatEnv, String tipoComprobante) {
        return sunatEnv + ":" + service(tipoComprobante);
    }

    /**
     * The route of a new document. Production requires a credential; beta and the fake fall back to
     * SUNAT's public user. Refuses routes the company may not use.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public Assignment assign(CompanyModel company, String sunatEnv, String tipoComprobante) {
        String service = service(tipoComprobante);
        if ("pse".equals(company.getEmissionMode()) && "production".equals(sunatEnv) && !"despatch".equals(service)) {
            // As PSE the platform sends with its own SOL users, spread over the pool (§10.2)
            UUID member = platformCredentials.pick("pse_sol").orElseThrow(() ->
                    ApiException.invalid("no_credentials", "La plataforma no tiene usuarios SOL de PSE activos.", null));
            return new Assignment(endpoint(sunatEnv, tipoComprobante), member);
        }
        // A company's real SOL/GRE credentials go to SUNAT production only, never to beta or the fake
        Optional<UUID> credential = "production".equals(sunatEnv)
                ? credentialId(company, "despatch".equals(service) ? "gre" : "sol")
                : Optional.empty();
        if ("production".equals(sunatEnv) && credential.isEmpty()) {
            throw ApiException.invalid("no_credentials", "despatch".equals(service)
                    ? "La empresa no tiene credenciales GRE (usuario SOL y cliente API) para enviar guías a SUNAT."
                    : "La empresa no tiene credenciales SOL para enviar a SUNAT.", null);
        }
        return new Assignment(endpoint(sunatEnv, tipoComprobante), credential.orElse(null));
    }

    /**
     * The route of a document that follows another (a voiding): a PSE document is voided by the
     * PSE, whatever the company's emission mode now.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public Assignment assignLike(CompanyModel company, String sunatEnv, String tipoComprobante, DocumentModel original) {
        if ("pse".equals(original.getEmissionMode()) && "production".equals(sunatEnv)) {
            // A PSE document is voided by the PSE too: the platform's pool
            UUID member = platformCredentials.pick("pse_sol").orElseThrow(() ->
                    ApiException.invalid("no_credentials", "La plataforma no tiene usuarios SOL de PSE activos.", null));
            return new Assignment(endpoint(sunatEnv, tipoComprobante), member);
        }
        String service = service(tipoComprobante);
        Optional<UUID> credential = "production".equals(sunatEnv) ? credentialId(company, "sol") : Optional.empty();
        if ("production".equals(sunatEnv) && credential.isEmpty()) {
            throw ApiException.invalid("no_credentials", "La empresa no tiene credenciales SOL para enviar a SUNAT.", null);
        }
        return new Assignment(sunatEnv + ":" + service, credential.orElse(null));
    }

    private Optional<UUID> credentialId(CompanyModel company, String kind) {
        return credentialProvider.getCredential(company.getId(), kind).map(c -> c.getId());
    }

    /** Reads the secret (audited) and builds the target. Runs with the company's tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Target target(TenantScope scope, CompanyModel company, DocumentModel document) {
        EngineModels.Environment environment = EngineModels.Environment.of(document.getSunatEnv());
        String kind = document.isGuia() ? "gre" : "sol";
        EngineModels.Credentials secret = null;
        if ("pse".equals(document.getEmissionMode()) && "production".equals(document.getSunatEnv()) && !document.isGuia()) {
            Map<String, String> fields = platformCredentials.read(document.getCredentialId(), "pse_sol", "send:" + document.getId());
            auditor.record(scope, "platform_credential.used", "document", document.getId(), Map.of("purpose", "pse_sol"));
            return new Target(environment, new EngineModels.Credentials(fields.get("username"), fields.get("password"), null, null));
        }
        if (document.getCredentialId() != null) {
            String purpose = "send:" + document.getId();
            Map<String, String> fields = credentials.read(scope, company, kind, purpose).orElseThrow(() -> new MissingCredentials(kind));
            secret = new EngineModels.Credentials(fields.get("username"), fields.get("password"), fields.get("client_id"),
                    fields.get("client_secret"));
        } else if (environment == EngineModels.Environment.PRODUCTION) {
            throw new MissingCredentials(kind);
        }
        return new Target(environment, secret);
    }

    /** The company has no credential to send this document with. */
    public static class MissingCredentials extends RuntimeException {
        public MissingCredentials(String kind) {
            super("La empresa no tiene credenciales " + kind.toUpperCase(java.util.Locale.ROOT) + " para enviar.");
        }
    }
}
