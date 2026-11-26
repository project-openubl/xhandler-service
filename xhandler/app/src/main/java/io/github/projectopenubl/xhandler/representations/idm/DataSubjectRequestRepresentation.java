package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "DataSubjectRequestView")
public record DataSubjectRequestRepresentation(
        UUID id,
        String subjectDocType,
        String subjectDocNumber,
        String subjectName,
        @Schema(description = "access, rectification, cancellation u opposition.") String kind,
        @Schema(description = "tenant (registrada por la organización) o platform (llegó a la plataforma).") String channel,
        String details,
        String receivedAt,
        String forwardedAt,
        String responseDueAt,
        @Schema(description = "open o closed.") String status,
        String resolution,
        String closedAt) {}
