package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter tokens (Nubefact-compatible clients); every method joins the caller's transaction. */
public interface AdapterTokenProvider {

    /** A token as listed: never the token itself. */
    record AdapterToken(UUID id, String kind, String label, String createdBy, Instant createdAt, Instant revokedAt) {}

    /** The tenant, company and token a token hash opens. */
    record TokenCompany(UUID tenantId, UUID companyId, String label, UUID tokenId) {}

    /** Stores a Nubefact token by its hash; runs with the tenant open. */
    void addNubefactToken(UUID id, UUID tenantId, UUID companyId, String tokenHash, String label, String createdBy);

    /** The company's tokens, newest first. */
    List<AdapterToken> getAdapterTokens(UUID companyId);

    /** Revokes an unrevoked token of the company; false if there is none. */
    boolean revokeAdapterToken(UUID tokenId, UUID companyId);

    /** What a Nubefact token hash opens, across tenants (SECURITY DEFINER {@code adapter_token_company_v2}). */
    Optional<TokenCompany> getNubefactTokenCompany(String tokenHash);
}
