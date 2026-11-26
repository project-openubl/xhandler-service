package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateWebhook")
public record CreateWebhookRepresentation(@NotBlank @Size(max = 2000) String url, @NotEmpty List<@NotBlank String> events) {}
