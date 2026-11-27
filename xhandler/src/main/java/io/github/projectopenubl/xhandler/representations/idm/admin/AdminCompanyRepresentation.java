package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A company of a tenant as the platform sees it, to hold it. */
@Schema(name = "AdminCompany")
public record AdminCompanyRepresentation(UUID id, String ruc, String razonSocial, String sunatEnv, String emissionMode, Instant rucVerifiedAt, boolean sendingPaused) {}
