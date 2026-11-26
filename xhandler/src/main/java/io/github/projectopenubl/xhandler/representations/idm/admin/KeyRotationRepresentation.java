package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** What a master-key rotation re-wrapped. */
@Schema(name = "KeyRotation")
public record KeyRotationRepresentation(int tenants, int credentials, int signingKeys) {}
