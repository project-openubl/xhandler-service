package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A forward SUNAT refused, waiting for a person. */
@Schema(name = "OseRejectedForward")
public record OseRejectedForwardRepresentation(UUID id, String name, String error, Instant cdrIssuedAt) {}
