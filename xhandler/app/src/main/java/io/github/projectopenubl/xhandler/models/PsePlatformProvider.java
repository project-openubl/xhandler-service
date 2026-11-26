package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The platform as PSE (DESIGN.md §3.2): tenants' authorizations and the rejected-CDR rate. Joins the caller's transaction. */
public interface PsePlatformProvider {

    /** {@code authorizedAt} is {@code String.valueOf} of the authorization date. */
    record PendingAuthorization(UUID id, UUID tenantId, UUID companyId, String ruc, String razonSocial, String authorizedAt,
            Boolean evidence) {}

    record Outcomes(long accepted, long rejected) {}

    /** Authorizations of every tenant waiting for a platform admin (SECURITY DEFINER). */
    List<PendingAuthorization> getPendingAuthorizations();

    /** The file reference of an authorization's evidence; empty when unknown or without evidence. */
    Optional<String> getAuthorizationEvidenceRef(UUID id);

    /** False when the authorization does not exist (or was not pending). */
    boolean approveAuthorization(UUID id, String by);

    /** Accepted and rejected CDRs of the platform as PSE in {@code [from, to)}. */
    Outcomes getOutcomes(Instant from, Instant to);

    /** The rate of the latest stored half-year report. */
    Optional<Double> getLatestReportRate();

    /** Inserts or overwrites the report of a half-year. */
    void saveReport(String period, long accepted, long rejected, BigDecimal rate);
}
