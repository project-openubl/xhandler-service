package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A tenant's plan as set by a platform admin. */
@Schema(name = "TenantPlan")
public record TenantPlanRepresentation(UUID tenantId, String plan) {}
