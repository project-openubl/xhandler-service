package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EnvironmentChange")
public record EnvironmentChangeRepresentation(@NotBlank @Pattern(regexp = "fake|beta|production") String sunatEnv) {}
