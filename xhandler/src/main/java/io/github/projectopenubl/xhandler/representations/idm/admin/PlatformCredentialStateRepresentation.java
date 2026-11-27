package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Whether a platform SOL user is in use. */
@Schema(name = "PlatformCredentialState")
public record PlatformCredentialStateRepresentation(UUID id, boolean active) {}
