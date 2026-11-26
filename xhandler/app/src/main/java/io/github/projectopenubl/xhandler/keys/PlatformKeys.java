package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.PlatformSecretProvider;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * The platform's own keys (DESIGN.md §6): the PSE signing key and the OSE CDR key. Separate,
 * permanent vault objects (an HSM in production), never mixed with tenant keys. Replacing one keeps
 * the old reference for retention, marked replaced.
 */
@ApplicationScoped
public class PlatformKeys {

    /** A platform key: a vault reference plus its certificate (DER). */
    public record Key(UUID id, String purpose, String keyRef, byte[] certificate, String subject, String ruc, Instant validUntil) {}

    private final PlatformSecretProvider platformSecretProvider;
    private final KeyVault vault;
    private final HsmExecutor hsm;

    public PlatformKeys(PlatformSecretProvider platformSecretProvider, KeyVault vault, HsmExecutor hsm) {
        this.platformSecretProvider = platformSecretProvider;
        this.vault = vault;
        this.hsm = hsm;
    }

    /** Imports a PKCS#12 as the platform's {@code purpose} key; the private key goes to the vault. */
    public Key importKey(String purpose, byte[] pkcs12, char[] password) {
        CertificateImporter.Imported imported;
        try {
            imported = CertificateImporter.read(pkcs12, password, Instant.now());
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(pkcs12, (byte) 0);
        }
        String keyRef = hsm.call(() -> vault.storePrivateKey(imported.privateKey(), imported.certificate()));
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                platformSecretProvider.replaceActivePlatformKey(purpose);
                UUID id = Ids.newId();
                platformSecretProvider.addPlatformKey(id, purpose, keyRef, imported.certificate().getEncoded(),
                        imported.certificate().getSubjectX500Principal().getName(), imported.ruc().orElse(null),
                        imported.certificate().getNotBefore().toInstant(), imported.certificate().getNotAfter().toInstant());
                return active(purpose).orElseThrow();
            });
        } catch (RuntimeException e) {
            // The transaction rolled back: don't leave the key in the vault
            hsm.call(() -> {
                vault.deletePrivateKey(keyRef);
                return null;
            });
            throw e;
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<Key> active(String purpose) {
        return platformSecretProvider.getActivePlatformKey(purpose).map(r -> new Key(r.id(), r.purpose(), r.keyRef(), r.certificate(),
                r.subject(), r.ruc(), r.validUntil()));
    }

    /** SHA256withRSA inside the vault, on the HSM executor. */
    public byte[] sign(Key key, byte[] data) {
        if (key.validUntil().isBefore(Instant.now())) {
            throw ApiException.invalid("platform_certificate_expired", "El certificado de la plataforma venció.", null);
        }
        return hsm.call(() -> vault.sign(key.keyRef(), data));
    }
}
