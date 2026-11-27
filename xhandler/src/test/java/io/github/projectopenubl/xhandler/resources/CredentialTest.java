package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.CredentialProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.github.projectopenubl.xhandler.managers.CredentialManager;
import io.github.projectopenubl.xhandler.managers.KeyRotationManager;
import io.github.projectopenubl.xhandler.models.CredentialModel;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.xml.EngineStub;
import io.github.projectopenubl.xhandler.keys.TestVaults;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class CredentialTest {

    @Inject
    CompanyProvider companyProvider;

    @Inject
    CredentialProvider credentialProvider;

    @Inject
    TenantAccess access;

    @Inject
    CredentialManager credentials;

    @Inject
    KeyRotationManager rotation;

    @Test
    void sol_credentials_are_encrypted_masked_and_audited() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        String path = "/api/v1/tenants/" + tenant + "/companies/" + company + "/credentials";

        as("alice").body(Map.of("username", "20123456789MODDATOS", "password", "s3cr3t-clave"))
                .put(path + "/sol").then().statusCode(200)
                .body("label", equalTo("2012…ATOS"))
                .body(not(containsString("s3cr3t-clave")));
        as("alice").get(path).then().statusCode(200)
                .body("find { it.kind == 'sol' }.label", equalTo("2012…ATOS"))
                .body(not(containsString("s3cr3t-clave")));

        // Reads and writes of secrets are audit log lines (DESIGN.md §16)
        List<String> audit = new CopyOnWriteArrayList<>();
        java.util.logging.Handler capture = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                audit.add(r instanceof org.jboss.logmanager.ExtLogRecord e ? e.getFormattedMessage() : r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        java.util.logging.Logger auditLogger = java.util.logging.Logger.getLogger("xhandler.audit");
        auditLogger.addHandler(capture);
        try {
            storeAndRead(tenant, company, audit);
        } finally {
            auditLogger.removeHandler(capture);
        }
    }

    private void storeAndRead(UUID tenant, UUID company, List<String> audit) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            CredentialModel stored = credentialProvider.getCredential(company, "sol").orElseThrow();
            String raw = new String(stored.getCiphertext(), StandardCharsets.ISO_8859_1);
            assertFalse(raw.contains("s3cr3t-clave"));
            assertEquals("test-2", stored.getMasterKeyId());

            Map<String, String> read = credentials.read(platform(tenant), companyProvider.getCompany(company).orElse(null), "sol", "test").orElseThrow();
            assertEquals("s3cr3t-clave", read.get("password"));
            assertTrue(audit.stream().anyMatch(l -> l.contains("action=credential.read") && l.contains(stored.getId().toString())), audit.toString());
        });
    }

    @Test
    void viewers_cant_write_credentials() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        as("bob").body(Map.of("username", "x", "password", "y"))
                .put("/api/v1/tenants/" + tenant + "/companies/" + company + "/credentials/sol")
                .then().statusCode(404);
    }

    @Test
    void rotation_rewraps_secrets_sealed_with_an_old_master_key() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());

        // A secret sealed with the previous master key (test-1); the app's current one is test-2
        SecretBox oldBox = new SecretBox(TestVaults.software("test-1"));
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenant);
            CredentialModel c = credentialProvider.newCredential(Ids.newId());
            c.setTenantId(tenant);
            c.setCompanyId(company);
            c.setKind("smtp");
            c.setLabel("smtp");
            c.seal(oldBox.seal("{\"password\":\"vieja\"}".getBytes(StandardCharsets.UTF_8), c.context()));
            credentialProvider.persist(c);
            return c.getId();
        });

        KeyRotationManager.Result result = rotation.rewrapAll();
        assertTrue(result.credentials() >= 1);

        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            CredentialModel c = credentialProvider.getCredential(id).orElse(null);
            assertEquals("test-2", c.getMasterKeyId());
            Map<String, String> read = credentials.read(platform(tenant), companyProvider.getCompany(company).orElse(null), "smtp", "test").orElseThrow();
            assertEquals("vieja", read.get("password"));
        });
    }

    @Test
    void a_ciphertext_only_opens_in_its_own_row() {
        SecretBox box = new SecretBox(TestVaults.software("test-2"));
        SecretBox.Sealed sealed = box.seal("clave".getBytes(StandardCharsets.UTF_8), "credential:a");
        assertEquals("clave", new String(box.open(sealed, "credential:a"), StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> box.open(sealed, "credential:b"));
    }

    private static TenantScope platform(UUID tenant) {
        return new TenantScope(tenant, new Actor("client", "test", false), Role.OWNER);
    }
}
