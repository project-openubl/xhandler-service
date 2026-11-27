package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A rejected forward queued again. */
@Schema(name = "OseReforward")
public record OseReforwardRepresentation(UUID id, String forwardStatus) {}
