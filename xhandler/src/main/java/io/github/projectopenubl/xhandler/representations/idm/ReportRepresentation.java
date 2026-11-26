package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ReportView")
public record ReportRepresentation(
        UUID id, String register, String format, String period, String sunatEnv, String status, String fileName, Integer rows,
        String detail, UUID jobId, Instant createdAt, Instant doneAt) {}
