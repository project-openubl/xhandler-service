package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RouteChange")
public record RouteChangeRepresentation(@NotBlank @Pattern(regexp = "sunat|ose") String submissionRoute, @Valid OseRepresentation ose) {}
