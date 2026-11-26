package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The company after a revocation: back in software mode. */
@Schema(name = "RevokedView")
public record RevokedRepresentation(UUID companyId, String emissionMode) {}
