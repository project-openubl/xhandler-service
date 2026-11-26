package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "DomainRequest")
public record DomainRequestRepresentation(@NotBlank @Pattern(regexp = "(?i)[a-z0-9-]+(\\.[a-z0-9-]+)+") String domain) {}
