package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A platform SOL user; the secret is sealed and never returned. */
@Schema(name = "PlatformCredential")
public record PlatformCredentialRepresentation(UUID id, String purpose, String label, boolean active) {}
