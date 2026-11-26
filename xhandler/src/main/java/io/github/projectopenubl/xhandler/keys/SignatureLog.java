package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.SignatureLogProvider;
import java.util.UUID;

/**
 * Logs every signature (DESIGN.md §6): who, document, key, time. Each entry commits on its own, so a
 * signature made in an emit that rolls back stays logged; its document id then matches no
 * document, which marks it discarded (§8).
 */
@ApplicationScoped
public class SignatureLog {

    private final SignatureLogProvider signatureLogProvider;
    private final TenantAccess access;

    public SignatureLog(SignatureLogProvider signatureLogProvider, TenantAccess access) {
        this.signatureLogProvider = signatureLogProvider;
        this.access = access;
    }

    public void record(UUID tenantId, UUID documentId, String serieNumero, UUID signingKeyId, String digestValue, String actor) {
        record(tenantId, documentId, serieNumero, signingKeyId, null, digestValue, actor);
    }

    public void record(UUID tenantId, UUID documentId, String serieNumero, UUID signingKeyId, UUID platformKeyId, String digestValue,
            String actor) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            signatureLogProvider.addSignature(Ids.newId(), tenantId, documentId, serieNumero, signingKeyId, platformKeyId, digestValue, actor);
        });
    }
}
