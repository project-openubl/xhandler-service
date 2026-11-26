package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** PSE authorizations of companies (DESIGN.md §1.1, §3.2); every method joins the caller's transaction, which entered the tenant. */
public interface PseProvider {

    record AuthorizationRow(UUID id, LocalDate authorizedAt, boolean evidence, String registeredBy, Instant approvedAt, Instant revokedAt) {}

    /** The company's authorizations, newest first. */
    List<AuthorizationRow> getAuthorizations(UUID companyId);

    /** True when the company has an authorization not revoked (approved or not). */
    boolean hasActiveAuthorization(UUID companyId);

    /** True when the company has an authorization not revoked and approved by a platform admin. */
    boolean hasApprovedAuthorization(UUID companyId);

    void addAuthorization(UUID id, UUID tenantId, UUID companyId, LocalDate authorizedAt, String evidenceRef, String registeredBy);

    /** Revokes the company's live authorizations; returns how many. */
    int revokeAuthorizations(UUID companyId, String revokedBy);

    /** The company's documents sent as PSE that have no final answer yet. */
    long countPendingPseDocuments(UUID companyId);
}
