package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A tenant's status after a lifecycle change. */
@Schema(name = "TenantStatus")
public record TenantStatusRepresentation(UUID tenantId, String status) {}
