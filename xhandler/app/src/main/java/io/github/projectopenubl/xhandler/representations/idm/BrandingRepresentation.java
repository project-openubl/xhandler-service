package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Branding")
public record BrandingRepresentation(@Pattern(regexp = "#[0-9a-fA-F]{6}") String color, @Size(max = 300) String footer,
        @Size(max = 200) String senderName) {}
