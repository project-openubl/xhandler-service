package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.SigningKeyProvider;
import io.github.projectopenubl.xhandler.models.CredentialProvider;
import io.github.projectopenubl.xhandler.keys.SecretBox;
import io.github.projectopenubl.xhandler.models.CredentialModel;

import io.github.projectopenubl.xhandler.keys.KeyVault;
import io.github.projectopenubl.xhandler.models.SigningKeyModel;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.PlatformSecretProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Re-wraps everything protected by an old master key with the current one, tenant by tenant, without
 * downtime: old master keys stay readable until nothing references them (DESIGN.md §6). Exposed
 * through xhandler-admin (P2).
 */
@ApplicationScoped
public class KeyRotation {

    private final SigningKeyProvider signingKeyProvider;

    private final CredentialProvider credentialProvider;

    private static final Logger LOG = Logger.getLogger(KeyRotation.class);

    public record Result(int tenants, int credentials, int signingKeys) {}

    private final PlatformSecretProvider platformSecretProvider;
    private final TenantAccess access;
    private final SecretBox box;
    private final KeyVault vault;

    public KeyRotation(PlatformSecretProvider platformSecretProvider, TenantAccess access, SecretBox box, KeyVault vault, CredentialProvider credentialProvider, SigningKeyProvider signingKeyProvider) {
        this.signingKeyProvider = signingKeyProvider;
        this.credentialProvider = credentialProvider;
        this.platformSecretProvider = platformSecretProvider;
        this.access = access;
        this.box = box;
        this.vault = vault;
    }

    public Result rewrapAll() {
        int credentials = 0;
        int keys = 0;
        List<UUID> tenants = QuarkusTransaction.requiringNew().call(this::tenantIds);
        for (UUID tenantId : tenants) {
            // One transaction per tenant, so a failure leaves the others done
            int[] counts = QuarkusTransaction.requiringNew().call(() -> rewrapTenant(tenantId));
            credentials += counts[0];
            keys += counts[1];
        }
        // The platform's own secrets and keys (PSE SOL pool, validity, OSE; PSE and OSE CDR keys)
        int[] platform = QuarkusTransaction.requiringNew().call(this::rewrapPlatform);
        credentials += platform[0];
        keys += platform[1];
        LOG.infof("key rotation: %d tenants, %d credentials and %d signing keys re-wrapped with %s",
                tenants.size(), credentials, keys, vault.currentMasterKeyId());
        return new Result(tenants.size(), credentials, keys);
    }

    private int[] rewrapPlatform() {
        String current = vault.currentMasterKeyId();
        List<PlatformSecretProvider.SealedPlatformCredential> secrets = platformSecretProvider.getPlatformCredentialsToRewrapForUpdate(current);
        for (PlatformSecretProvider.SealedPlatformCredential r : secrets) {
            SecretBox.Sealed rewrapped = box.rewrap(new SecretBox.Sealed(r.ciphertext(), r.wrappedDataKey(), r.masterKeyId()));
            platformSecretProvider.setPlatformCredentialWrappedKey(r.id(), rewrapped.wrappedDataKey(), rewrapped.masterKeyId());
        }
        int keys = 0;
        for (PlatformSecretProvider.PlatformKeyRef r : platformSecretProvider.getPlatformKeyRefsForUpdate()) {
            String rewrapped = vault.rewrapPrivateKey(r.keyRef());
            if (!rewrapped.equals(r.keyRef())) {
                // key_ref is not updatable by the app role; the owner-run function does it
                platformSecretProvider.rewrapPlatformKey(r.id(), rewrapped);
                keys++;
            }
        }
        return new int[] {secrets.size(), keys};
    }

    private List<UUID> tenantIds() {
        return platformSecretProvider.getAllTenantIds();
    }

    private int[] rewrapTenant(UUID tenantId) {
        access.enterAsPlatform(tenantId);
        String current = vault.currentMasterKeyId();
        // Locked, so a concurrent update of the secret can't be overwritten with the old ciphertext
        List<CredentialModel> credentials = credentialProvider.getCredentialsToRewrapForUpdate(current);
        for (CredentialModel credential : credentials) {
            credential.seal(box.rewrap(credential.sealed()));
        }
        int keys = 0;
        // Every key, not only active ones: replaced keys stay readable for retention
        for (SigningKeyModel key : signingKeyProvider.getSigningKeysForUpdate()) {
            String rewrapped = vault.rewrapPrivateKey(key.getKeyRef());
            if (!rewrapped.equals(key.getKeyRef())) {
                key.setKeyRef(rewrapped);
                keys++;
            }
        }
        return new int[] {credentials.size(), keys};
    }
}
