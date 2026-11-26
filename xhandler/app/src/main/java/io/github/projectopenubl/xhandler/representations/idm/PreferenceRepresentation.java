package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Preference")
public record PreferenceRepresentation(@NotBlank String kind, boolean email, boolean inApp) {}
