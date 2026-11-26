package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.managers.CompanyManager;
import io.github.projectopenubl.xhandler.models.SigningKeyProvider;
import io.github.projectopenubl.xhandler.keys.CertificateImporter;
import io.github.projectopenubl.xhandler.keys.KeyVault;
import io.github.projectopenubl.xhandler.models.SigningKeyModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** A company's signing certificate (.pfx). The private key goes into the {@link KeyVault}. */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/certificate")
public class CertificateResource {

    private final CompanyManager companyManager;

    private final SigningKeyProvider signingKeyProvider;

    public record CertificateView(String subject, String issuer, String serial, String ruc, Instant validFrom, Instant validUntil, boolean rucMatches) {}

    private final TenantAccess access;
    private final KeyVault vault;
    private final AuditManager auditor;
    private final jakarta.transaction.TransactionSynchronizationRegistry transactions;

    public CertificateResource(
            TenantAccess access, KeyVault vault, AuditManager auditor, jakarta.transaction.TransactionSynchronizationRegistry transactions, SigningKeyProvider signingKeyProvider, CompanyManager companyManager) {
        this.companyManager = companyManager;
        this.signingKeyProvider = signingKeyProvider;
        this.access = access;
        this.vault = vault;
        this.auditor = auditor;
        this.transactions = transactions;
    }

    /** Uploads a .pfx (multipart: {@code file}, {@code password}), replacing the active key. */
    @Operation(operationId = "uploadCertificate")
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public CertificateView upload(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("companyId") UUID companyId,
            @RestForm("file") FileUpload file,
            @RestForm("password") String password) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        // Serializes concurrent uploads for the same company
        CompanyModel company = companyManager.getCompanyForUpdate(companyId);
        if (file == null || password == null) {
            throw ApiException.invalid("invalid_request", "Debe enviar el archivo .pfx y su contraseña.", null);
        }

        byte[] pkcs12 = readFile(file);
        char[] secret = password.toCharArray();
        CertificateImporter.Imported imported;
        try {
            imported = CertificateImporter.read(pkcs12, secret, Instant.now());
        } finally {
            Arrays.fill(secret, '\0');
            Arrays.fill(pkcs12, (byte) 0);
        }

        java.util.Optional<SigningKeyModel> previous = signingKeyProvider.getActiveSigningKey(company.getId());
        previous.ifPresent(p -> p.setStatus("replaced"));
        // The previous key must stop being "active" before the new one is inserted (unique index)
        signingKeyProvider.flush();

        SigningKeyModel key = signingKeyProvider.newSigningKey(Ids.newId());
        key.setTenantId(tenantId);
        key.setCompanyId(company.getId());
        key.setKeyRef(vault.storePrivateKey(imported.privateKey(), imported.certificate()));
        // Keep the vault in step with the database: drop the new key if the transaction rolls
        // back, and the replaced one once it commits
        String newRef = key.getKeyRef();
        String replacedRef = previous.map(p -> p.getKeyRef()).orElse(null);
        transactions.registerInterposedSynchronization(new jakarta.transaction.Synchronization() {
            @Override
            public void beforeCompletion() {}

            @Override
            public void afterCompletion(int status) {
                if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                    if (replacedRef != null) {
                        vault.deletePrivateKey(replacedRef);
                    }
                } else {
                    vault.deletePrivateKey(newRef);
                }
            }
        });
        try {
            key.setCertificate(imported.certificate().getEncoded());
        } catch (CertificateEncodingException e) {
            throw ApiException.invalid("invalid_certificate", "El certificado digital no es válido.", e.getMessage());
        }
        key.setSubject(imported.certificate().getSubjectX500Principal().getName());
        key.setIssuer(imported.certificate().getIssuerX500Principal().getName());
        key.setSerial(imported.certificate().getSerialNumber().toString(16));
        key.setRuc(imported.ruc().orElse(null));
        key.setValidFrom(imported.certificate().getNotBefore().toInstant());
        key.setValidUntil(imported.certificate().getNotAfter().toInstant());
        signingKeyProvider.persist(key);

        // A matching RUC is informative only: anyone can make a self-signed certificate with any
        // RUC. The RUC is verified by a SUNAT SOL login (P3), or later by checking the chain against
        // the CAs SUNAT accepts.
        boolean rucMatches = company.getRuc().equals(key.getRuc());
        auditor.record(scope, "certificate.imported", "signing_key", key.getId(),
                Map.of("company_id", company.getId().toString(), "serial", key.getSerial(), "ruc_matches", rucMatches));
        return view(key, company);
    }

    @Operation(operationId = "getCertificate")
    @GET
    @Transactional
    public CertificateView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        CompanyModel company = companyManager.getCompany(companyId);
        return signingKeyProvider.getActiveSigningKey(company.getId()).map(k -> view(k, company)).orElseThrow(ApiException::notFound);
    }

    private static CertificateView view(SigningKeyModel k, CompanyModel company) {
        return new CertificateView(k.getSubject(), k.getIssuer(), k.getSerial(), k.getRuc(), k.getValidFrom(), k.getValidUntil(), company.getRuc().equals(k.getRuc()));
    }

    private static byte[] readFile(FileUpload file) {
        try {
            return Files.readAllBytes(file.uploadedFile());
        } catch (IOException e) {
            throw ApiException.invalid("invalid_request", "No se pudo leer el archivo enviado.", null);
        } finally {
            try {
                Files.deleteIfExists(file.uploadedFile());
            } catch (IOException ignored) {
                // The temporary upload is cleaned up by Quarkus as well
            }
        }
    }
}
