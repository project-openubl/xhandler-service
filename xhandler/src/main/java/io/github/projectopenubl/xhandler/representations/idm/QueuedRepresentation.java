package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Queued")
public record QueuedRepresentation(UUID jobId, String jobPrn) {}
