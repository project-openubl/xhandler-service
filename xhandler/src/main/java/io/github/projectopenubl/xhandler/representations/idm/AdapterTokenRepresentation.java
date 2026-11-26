package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** An adapter token as listed: never the token itself. */
@Schema(name = "AdapterTokenView")
public record AdapterTokenRepresentation(UUID id, String kind, String label, String createdBy, java.time.Instant createdAt,
        java.time.Instant revokedAt) {}
