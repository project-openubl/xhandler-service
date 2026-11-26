package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A PSE authorization registered by a tenant, waiting for a platform admin to check its evidence. */
@Schema(name = "PendingPseAuthorization")
public record PendingPseAuthorizationRepresentation(UUID id, UUID tenantId, UUID companyId, String ruc, String razonSocial, String authorizedAt, Boolean evidence) {}
