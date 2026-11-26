package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
/** Error body of every API error: {@code kind} is stable, {@code message} is Spanish. */
@Schema(name = "ApiError")
public record ErrorRepresentation(String kind, String message, String detail) {}
