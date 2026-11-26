package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Buyer capability links (DESIGN.md §12); every method joins the caller's transaction. */
public interface AccessLinkProvider {

    /** The document a link opens. */
    record LinkedDocument(UUID tenantId, UUID documentId) {}

    /** Stores a link by its token hash; runs with the document's tenant open. */
    void addAccessLink(UUID id, UUID tenantId, UUID documentId, String tokenHash, Instant expiresAt);

    /** The document of a valid link, across tenants (SECURITY DEFINER {@code access_token_document}). */
    Optional<LinkedDocument> getLinkedDocument(String tokenHash);
}
