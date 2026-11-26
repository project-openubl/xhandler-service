package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Submission")
public record SubmissionRepresentation(@NotBlank String fileName, @NotBlank String contentBase64) {}
