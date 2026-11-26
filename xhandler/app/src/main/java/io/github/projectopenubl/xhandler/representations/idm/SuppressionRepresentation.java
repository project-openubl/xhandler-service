package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Suppression")
public record SuppressionRepresentation(String address, String reason, Instant createdAt) {}
