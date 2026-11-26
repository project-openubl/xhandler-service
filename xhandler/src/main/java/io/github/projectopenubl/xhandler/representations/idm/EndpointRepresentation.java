package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Endpoint")
public record EndpointRepresentation(String id, String breakerState, Instant breakerUntil, int concurrencyCap, boolean paused) {}
