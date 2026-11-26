package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "LookupResult")
public record LookupResultRepresentation(String status) {}
