package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "JobView")
public record JobRepresentation(
        UUID id,
        String prn,
        String name,
        String state,
        int priorityClass,
        Instant createdAt,
        Instant unblockedAt,
        Instant startedAt,
        Instant finishedAt,
        String worker,
        int attempt,
        List<String> reservedResources,
        List<String> createdResources,
        Map<String, Object> result,
        Map<String, Object> error,
        String correlationId) {}
