package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Email suppressions, sender domains and the delivery log (DESIGN.md §11.6, §26.5); every method
 * joins the caller's transaction, which entered the tenant. Addresses are normalized (stripped,
 * lower case) here.
 */
public interface EmailProvider {

    record EmailSuppression(String address, String reason, Instant createdAt) {}

    record SenderDomain(UUID id, String domain, String verificationToken, Instant verifiedAt) {}

    /** Newest first. */
    List<EmailSuppression> getEmailSuppressions();

    /** Whether the address hard-bounced or complained. */
    boolean isEmailSuppressed(String address);

    /** False when the address wasn't suppressed. */
    boolean deleteEmailSuppression(String address);

    /** Inserts the suppression, or replaces its reason. */
    void suppressEmail(UUID tenantId, String address, String reason);

    /** Adds the domain unless the tenant already has it. */
    void addSenderDomain(UUID id, UUID tenantId, String domain, String verificationToken);

    /** Ordered by domain. */
    List<SenderDomain> getSenderDomains();

    Optional<SenderDomain> getSenderDomain(String domain);

    void markSenderDomainVerified(String domain);

    boolean isSenderDomainVerified(String domain);

    /** Emails logged in the last 24 hours. */
    long countEmailsSentLastDay();

    void addEmailDelivery(UUID id, UUID tenantId, UUID documentId, String recipient, String result, String detail);
}
