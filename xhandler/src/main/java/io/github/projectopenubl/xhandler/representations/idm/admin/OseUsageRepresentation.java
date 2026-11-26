package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Validations of an OSE user in a month (the separate billing line). */
@Schema(name = "OseUsage")
public record OseUsageRepresentation(UUID oseClientId, String issuerRuc, String username, UUID tenantId, long validations) {}
