package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.SignatureLogProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaSignatureLogProvider implements SignatureLogProvider {

    private final EntityManager em;

    public JpaSignatureLogProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void addSignature(UUID id, UUID tenantId, UUID documentId, String serieNumero, UUID signingKeyId, UUID platformKeyId,
            String digestValue, String actor) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.signature_log (id, tenant_id, document_id, serie_numero, signing_key_id, platform_key_id,
                            digest_value, actor)
                        VALUES (:id, :tenant, :document, :serie, :key, :platformKey, :digest, :actor)""")
                .setParameter("platformKey", platformKeyId)
                .setParameter("id", id)
                .setParameter("tenant", tenantId)
                .setParameter("document", documentId)
                .setParameter("serie", serieNumero)
                .setParameter("key", signingKeyId)
                .setParameter("digest", digestValue)
                .setParameter("actor", actor)
                .execute();
    }
}
