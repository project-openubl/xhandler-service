package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CredentialView")
public record CredentialRepresentation(String kind, String label, Instant updatedAt) {}
