package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A tenant as the platform sees it: account metadata, never its documents. {@code plan_source}: default, lago or admin. */
@Schema(name = "AdminTenant")
public record AdminTenantRepresentation(UUID id, String name, String plan, String planSource, String status, UUID partnerId, String partnerName, String createdBy, Instant createdAt, boolean sendingPaused, boolean auditHold, boolean archivePlan, Instant deleteAfter, long companies) {}
