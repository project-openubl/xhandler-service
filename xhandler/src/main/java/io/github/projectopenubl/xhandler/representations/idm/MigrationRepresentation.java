package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Migration")
public record MigrationRepresentation(String version, String description, String state, java.time.LocalDateTime installedOn) {}
