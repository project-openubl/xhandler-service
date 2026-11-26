package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.CredentialProvider;
import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.github.projectopenubl.xhandler.models.CredentialModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** Stores and reads company secrets. Every read of a secret is audited. */
@ApplicationScoped
public class CredentialManager {

    private final CredentialProvider credentialProvider;

    private final SecretBox box;
    private final AuditManager auditor;
    private final ObjectMapper json;

    public CredentialManager(SecretBox box, AuditManager auditor, ObjectMapper json, CredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
        this.box = box;
        this.auditor = auditor;
        this.json = json;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public CredentialModel store(TenantScope scope, CompanyModel company, String kind, Map<String, String> fields, String label) {
        CredentialModel credential = credentialProvider.getCredential(company.getId(), kind)
                .orElseGet(() -> {
                    CredentialModel c = credentialProvider.newCredential(Ids.newId());
                    c.setTenantId(company.getTenantId());
                    c.setCompanyId(company.getId());
                    c.setKind(kind);
                    return c;
                });
        credential.setLabel(label);
        credential.seal(box.seal(toJson(fields), credential.context()));
        credentialProvider.persist(credential);
        auditor.record(scope, "credential.stored", "credential", credential.getId(), Map.of("kind", kind, "company_id", company.getId().toString()));
        return credential;
    }

    /** The decrypted fields of a secret, for sending to SUNAT etc. Audited. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Map<String, String>> read(TenantScope scope, CompanyModel company, String kind, String purpose) {
        return credentialProvider.getCredential(company.getId(), kind)
                .map(credential -> {
                    auditor.record(scope, "credential.read", "credential", credential.getId(), Map.of("kind", kind, "purpose", purpose));
                    return fromJson(box.open(credential.sealed(), credential.context()));
                });
    }

    private byte[] toJson(Map<String, String> fields) {
        try {
            return json.writeValueAsBytes(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> fromJson(byte[] bytes) {
        try {
            return json.readValue(new String(bytes, StandardCharsets.UTF_8), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new ApiException(500, "corrupt_secret", "Error interno al leer una credencial.");
        }
    }
}
