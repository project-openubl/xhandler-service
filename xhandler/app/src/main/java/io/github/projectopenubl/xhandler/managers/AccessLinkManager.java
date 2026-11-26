package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.DocumentModel;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.files.FileStore;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.AccessLinkProvider;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Buyer capability links (DESIGN.md §12): a random 128-bit token per document, stored hashed. The
 * link opens the document's XML, CDR and PDF for the legal access period (1 year, §3.3).
 */
@ApplicationScoped
public class AccessLinkManager {

    /** A link resolved to its document. */
    public record Resolved(UUID tenantId, UUID documentId) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccessLinkProvider accessLinkProvider;
    private final String publicBaseUrl;
    private final Duration validity;

    public AccessLinkManager(
            AccessLinkProvider accessLinkProvider,
            @ConfigProperty(name = "xhandler.public-url", defaultValue = "http://localhost:8080") String publicBaseUrl,
            @ConfigProperty(name = "xhandler.access-link.validity", defaultValue = "366d") Duration validity) {
        this.accessLinkProvider = accessLinkProvider;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.validity = validity;
    }

    /** Creates a link; the token is only ever returned here. Runs with the document's tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public String create(DocumentModel document) {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        accessLinkProvider.addAccessLink(Ids.newId(), document.getTenantId(), document.getId(), hash(token), Instant.now().plus(validity));
        return token;
    }

    /**
     * The buyer's page in the UI (printed in PDFs, sent in emails, returned as access_url): it reads
     * {@code GET /api/v1/public/documents/{token}}, the API behind it.
     */
    public String url(String token) {
        return publicBaseUrl + "/p/documentos/" + token;
    }

    /** The document a token opens, if valid (cross-tenant lookup through a SECURITY DEFINER function). */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Resolved> resolve(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{22}")) {
            return Optional.empty();
        }
        return accessLinkProvider.getLinkedDocument(hash(token)).map(r -> new Resolved(r.tenantId(), r.documentId()));
    }

    static String hash(String token) {
        return FileStore.sha256(token.getBytes(StandardCharsets.US_ASCII));
    }
}
