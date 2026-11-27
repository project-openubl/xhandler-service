package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A data-subject request registered by the platform and forwarded to the tenant. */
@Schema(name = "DataSubjectRequestForwarded")
public record DataSubjectRequestForwardedRepresentation(UUID id, UUID tenantId, boolean forwarded) {}
