package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Event")
public record DocumentEventRepresentation(Instant at, String status, Integer code, String message, String actor, String correlationId) {}
