package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EmailRequest")
public record EmailRequestRepresentation(@NotEmpty @Size(max = 10) List<String> to, @Size(max = 10) List<String> cc, Boolean includeCdr) {}
