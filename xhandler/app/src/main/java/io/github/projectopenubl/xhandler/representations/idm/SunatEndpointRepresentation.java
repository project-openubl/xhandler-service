package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** One SUNAT web service of an environment, as its circuit breaker sees it (DESIGN.md §24). */
@Schema(name = "SunatEndpoint")
public record SunatEndpointRepresentation(
        @Schema(description = "Servicio de SUNAT: invoice (comprobantes y resúmenes), despatch (guías) o perception (percepciones y retenciones).") String service,
        @Schema(description = "closed (responde), open (caído: no se le envía) o half_open (probando si volvió).") String breakerState,
        @Schema(description = "Hasta cuándo queda abierto el disyuntor; luego se prueba de nuevo.") Instant breakerUntil,
        @Schema(description = "Pausado por la plataforma: no se le envía.") boolean paused) {}
