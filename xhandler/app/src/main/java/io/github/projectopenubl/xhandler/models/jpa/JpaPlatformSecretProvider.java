package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.PlatformSecretProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaPlatformSecretProvider implements PlatformSecretProvider {

    private final EntityManager em;

    public JpaPlatformSecretProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public void addPlatformCredential(UUID id, String purpose, String label, byte[] ciphertext, byte[] wrappedDataKey, String masterKeyId) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.platform_credential (id, purpose, label, ciphertext, wrapped_data_key, master_key_id)
                        VALUES (:id, :p, :l, :c, :w, :m)""")
                .setParameter("id", id).setParameter("p", purpose).setParameter("l", label).setParameter("c", ciphertext)
                .setParameter("w", wrappedDataKey).setParameter("m", masterKeyId).executeUpdate();
    }

    @Override
    public List<PlatformCredentialRow> getPlatformCredentials(String purpose) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, purpose, label, active FROM xhandler.platform_credential WHERE purpose = :p ORDER BY label")
                .setParameter("p", purpose).getResultList();
        return rows.stream().map(r -> new PlatformCredentialRow((UUID) r[0], (String) r[1], (String) r[2], (Boolean) r[3])).toList();
    }

    @Override
    public boolean setPlatformCredentialActive(UUID id, boolean active) {
        return em.createNativeQuery("UPDATE xhandler.platform_credential SET active = :a, updated_at = now() WHERE id = :id")
                .setParameter("a", active).setParameter("id", id).executeUpdate() == 1;
    }

    @Override
    public List<UUID> getActivePlatformCredentialIds(String purpose) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT id FROM xhandler.platform_credential WHERE purpose = :p AND active")
                .setParameter("p", purpose).getResultList();
        return ids;
    }

    @Override
    public boolean existsPlatformCredential(UUID id, String purpose) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.platform_credential WHERE id = :id AND purpose = :p")
                .setParameter("id", id).setParameter("p", purpose).getResultList().isEmpty();
    }

    @Override
    public Optional<SealedPlatformCredential> getActivePlatformCredential(UUID id, String purpose) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, ciphertext, wrapped_data_key, master_key_id, active FROM xhandler.platform_credential
                        WHERE purpose = :p AND (id = :id OR active) ORDER BY (id = :id AND active) DESC, random() LIMIT 1""")
                .setParameter("p", purpose).setParameter("id", id).getResultList();
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0)[4])) {
            return Optional.empty();
        }
        Object[] r = rows.get(0);
        return Optional.of(new SealedPlatformCredential((UUID) r[0], (byte[]) r[1], (byte[]) r[2], (String) r[3]));
    }

    @Override
    public List<SealedPlatformCredential> getPlatformCredentialsToRewrapForUpdate(String masterKeyId) {
        @SuppressWarnings("unchecked")
        List<Object[]> secrets = em.createNativeQuery("""
                        SELECT id, ciphertext, wrapped_data_key, master_key_id FROM xhandler.platform_credential
                        WHERE master_key_id <> :m FOR UPDATE""").setParameter("m", masterKeyId).getResultList();
        return secrets.stream().map(r -> new SealedPlatformCredential((UUID) r[0], (byte[]) r[1], (byte[]) r[2], (String) r[3])).toList();
    }

    @Override
    public void setPlatformCredentialWrappedKey(UUID id, byte[] wrappedDataKey, String masterKeyId) {
        em.createNativeQuery("UPDATE xhandler.platform_credential SET wrapped_data_key = :w, master_key_id = :m, updated_at = now() WHERE id = :id")
                .setParameter("w", wrappedDataKey).setParameter("m", masterKeyId).setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public List<PlatformKeyRef> getPlatformKeyRefsForUpdate() {
        @SuppressWarnings("unchecked")
        List<Object[]> platformKeys = em.createNativeQuery("SELECT id, key_ref FROM xhandler.platform_key FOR UPDATE").getResultList();
        return platformKeys.stream().map(r -> new PlatformKeyRef((UUID) r[0], (String) r[1])).toList();
    }

    @Override
    public void rewrapPlatformKey(UUID id, String keyRef) {
        em.createNativeQuery("SELECT xhandler.rewrap_platform_key(:id, :ref)").setParameter("id", id).setParameter("ref", keyRef)
                .getSingleResult();
    }

    @Override
    public List<UUID> getAllTenantIds() {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT id FROM xhandler.all_tenant_ids()").getResultList();
        return ids;
    }

    @Override
    public void replaceActivePlatformKey(String purpose) {
        em.createNativeQuery("UPDATE xhandler.platform_key SET status = 'replaced' WHERE purpose = :p AND status = 'active'")
                .setParameter("p", purpose).executeUpdate();
    }

    @Override
    public void addPlatformKey(UUID id, String purpose, String keyRef, byte[] certificate, String subject, String ruc, Instant validFrom,
            Instant validUntil) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.platform_key (id, purpose, key_ref, certificate, subject, ruc, valid_from, valid_until)
                        VALUES (:id, :p, :ref, :cert, :subject, :ruc, :from, :until)""")
                .setParameter("id", id).setParameter("p", purpose).setParameter("ref", keyRef)
                .setParameter("cert", certificate)
                .setParameter("subject", subject)
                .setParameter("ruc", ruc)
                .setParameter("from", validFrom)
                .setParameter("until", validUntil)
                .executeUpdate();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<PlatformKeyRow> getActivePlatformKey(String purpose) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, purpose, key_ref, certificate, subject, ruc, valid_until FROM xhandler.platform_key WHERE purpose = :p AND status = 'active'")
                .setParameter("p", purpose).getResultList();
        return rows.stream().findFirst().map(r -> new PlatformKeyRow((UUID) r[0], (String) r[1], (String) r[2], (byte[]) r[3], (String) r[4],
                (String) r[5], Times.toInstant(r[6])));
    }
}
