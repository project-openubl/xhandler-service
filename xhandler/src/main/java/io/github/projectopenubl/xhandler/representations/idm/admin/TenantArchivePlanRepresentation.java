package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A tenant's archive plan. */
@Schema(name = "TenantArchivePlan")
public record TenantArchivePlanRepresentation(UUID tenantId, boolean archivePlan) {}
