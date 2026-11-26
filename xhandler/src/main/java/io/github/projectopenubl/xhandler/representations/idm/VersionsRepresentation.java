package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Versions")
public record VersionsRepresentation(String service, String engine, String xhandlerRust) {}
