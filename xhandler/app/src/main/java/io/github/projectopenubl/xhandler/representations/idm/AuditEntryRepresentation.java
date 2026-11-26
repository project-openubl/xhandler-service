package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AuditEntry")
public record AuditEntryRepresentation(Instant at, String actor, String action, String objectType, String objectId, Map<String, Object> details) {}
