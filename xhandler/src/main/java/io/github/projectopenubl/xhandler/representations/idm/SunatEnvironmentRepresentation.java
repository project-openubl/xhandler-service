package io.github.projectopenubl.xhandler.representations.idm;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SUNAT availability per environment, from the breakers of its services. {@code available}: every
 * service seen so far answers (breaker closed, not paused); an environment nothing was sent to
 * yet has no services and counts as available.
 */
@Schema(name = "SunatEnvironment")
public record SunatEnvironmentRepresentation(
        @Schema(description = "production, beta o fake.") String environment,
        boolean available,
        List<SunatEndpointRepresentation> endpoints) {}
