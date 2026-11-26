package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.keys.SecretBox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.PlatformSecretProvider;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jboss.logging.Logger;

/**
 * The platform's own credentials (DESIGN.md §7.1): the pool of PSE SOL users, the OSE's SUNAT
 * credentials, the validity API. Sealed like tenant secrets; every read is logged (no tenant audit
 * log owns them). PSE documents spread over the pool, so no single SOL user is a bottleneck (§10.2).
 */
@ApplicationScoped
public class PlatformCredentials {

    /** A pool member without its secret. */
    public record Entry(UUID id, String purpose, String label, boolean active) {}

    static final Logger AUDIT = Logger.getLogger("xhandler.audit.platform");

    private final PlatformSecretProvider platformSecretProvider;
    private final SecretBox box;
    private final ObjectMapper json;

    public PlatformCredentials(PlatformSecretProvider platformSecretProvider, SecretBox box, ObjectMapper json) {
        this.platformSecretProvider = platformSecretProvider;
        this.box = box;
        this.json = json;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Entry add(String purpose, String label, Map<String, String> fields) {
        UUID id = Ids.newId();
        SecretBox.Sealed sealed;
        try {
            sealed = box.seal(json.writeValueAsBytes(fields), context(id, purpose));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        platformSecretProvider.addPlatformCredential(id, purpose, label, sealed.ciphertext(), sealed.wrappedDataKey(), sealed.masterKeyId());
        return new Entry(id, purpose, label, true);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public List<Entry> list(String purpose) {
        return platformSecretProvider.getPlatformCredentials(purpose).stream().map(r -> new Entry(r.id(), r.purpose(), r.label(), r.active()))
                .toList();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean setActive(UUID id, boolean active) {
        return platformSecretProvider.setPlatformCredentialActive(id, active);
    }

    /** An active pool member, chosen at random so documents spread over the pool. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<UUID> pick(String purpose) {
        List<UUID> ids = platformSecretProvider.getActivePlatformCredentialIds(purpose);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
    }

    /** The secret fields of a credential; an inactive one is replaced by another active member. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Map<String, String> read(UUID id, String purpose, String why) {
        // The id must exist for this purpose; only an inactive (retired) member is replaced
        boolean exists = platformSecretProvider.existsPlatformCredential(id, purpose);
        if (!exists) {
            throw new IllegalStateException("platform credential " + id + " is not a " + purpose + " credential");
        }
        PlatformSecretProvider.SealedPlatformCredential r = platformSecretProvider.getActivePlatformCredential(id, purpose)
                .orElseThrow(() -> ApiException.invalid("no_credentials", "La plataforma no tiene credenciales activas para " + purpose + ".", null));
        AUDIT.infof("platform credential %s (%s) read for %s", r.id(), purpose, why);
        try {
            return json.readValue(box.open(new SecretBox.Sealed(r.ciphertext(), r.wrappedDataKey(), r.masterKeyId()), context(r.id(), purpose)),
                    new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String context(UUID id, String purpose) {
        return "platform-credential:" + id + ":" + purpose;
    }
}
