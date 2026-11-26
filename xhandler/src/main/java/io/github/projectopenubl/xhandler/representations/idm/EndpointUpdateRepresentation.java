package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.Min;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EndpointUpdate")
public record EndpointUpdateRepresentation(@Min(1) Integer concurrencyCap) {}
