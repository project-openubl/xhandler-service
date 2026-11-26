package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A tenant's tax-audit hold. */
@Schema(name = "TenantAuditHold")
public record TenantAuditHoldRepresentation(UUID tenantId, boolean auditHold) {}
