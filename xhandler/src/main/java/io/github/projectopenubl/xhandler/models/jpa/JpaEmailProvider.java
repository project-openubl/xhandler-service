package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.EmailProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaEmailProvider implements EmailProvider {

    private final EntityManager em;

    public JpaEmailProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<EmailSuppression> getEmailSuppressions() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT address, reason, created_at FROM xhandler.email_suppression ORDER BY created_at DESC")
                .getResultList();
        return rows.stream().map(r -> new EmailSuppression((String) r[0], (String) r[1], Times.toInstant(r[2]))).toList();
    }

    @Override
    public boolean isEmailSuppressed(String address) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.email_suppression WHERE address = :a")
                .setParameter("a", normalize(address)).getResultList().isEmpty();
    }

    @Override
    public boolean deleteEmailSuppression(String address) {
        int removed = em.createNativeStatement("DELETE FROM xhandler.email_suppression WHERE address = :a")
                .setParameter("a", normalize(address)).execute();
        return removed != 0;
    }

    @Override
    public void suppressEmail(UUID tenantId, String address, String reason) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.email_suppression (tenant_id, address, reason) VALUES (:t, :a, :r)
                        ON CONFLICT (tenant_id, address) DO UPDATE SET reason = excluded.reason""")
                .setParameter("t", tenantId).setParameter("a", normalize(address)).setParameter("r", reason).execute();
    }

    @Override
    public void addSenderDomain(UUID id, UUID tenantId, String domain, String verificationToken) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.sender_domain (id, tenant_id, domain, verification_token) VALUES (:id, :t, :d, :token)
                        ON CONFLICT (tenant_id, domain) DO NOTHING""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("d", domain)
                .setParameter("token", verificationToken).execute();
    }

    @Override
    public List<SenderDomain> getSenderDomains() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, domain, verification_token, verified_at FROM xhandler.sender_domain ORDER BY domain")
                .getResultList();
        return rows.stream().map(JpaEmailProvider::senderDomain).toList();
    }

    @Override
    public Optional<SenderDomain> getSenderDomain(String domain) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, domain, verification_token, verified_at FROM xhandler.sender_domain WHERE domain = :d")
                .setParameter("d", domain).getResultList();
        return rows.stream().findFirst().map(JpaEmailProvider::senderDomain);
    }

    private static SenderDomain senderDomain(Object[] r) {
        return new SenderDomain((UUID) r[0], (String) r[1], (String) r[2], Times.toInstant(r[3]));
    }

    @Override
    public void markSenderDomainVerified(String domain) {
        em.createNativeStatement("UPDATE xhandler.sender_domain SET verified_at = coalesce(verified_at, now()) WHERE domain = :d")
                .setParameter("d", domain).execute();
    }

    @Override
    public boolean isSenderDomainVerified(String domain) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.sender_domain WHERE domain = :d AND verified_at IS NOT NULL")
                .setParameter("d", domain).getResultList().isEmpty();
    }

    @Override
    public void addEmailDelivery(UUID id, UUID tenantId, UUID documentId, String recipient, String result, String detail) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.delivery (id, tenant_id, document_id, channel, recipient, result, detail)
                        VALUES (:id, :tenant, :document, 'email', :recipient, :result, :detail)""")
                .setParameter("id", id)
                .setParameter("tenant", tenantId)
                .setParameter("document", documentId)
                .setParameter("recipient", recipient)
                .setParameter("result", result)
                .setParameter("detail", detail)
                .execute();
    }

    static String normalize(String address) {
        return address.strip().toLowerCase(Locale.ROOT);
    }
}
