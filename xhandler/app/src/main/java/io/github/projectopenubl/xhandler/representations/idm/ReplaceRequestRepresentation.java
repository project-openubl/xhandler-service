package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ReplaceRequest")
public record ReplaceRequestRepresentation(Boolean acknowledgeDifferences, Boolean confirmNotReceived) {}
