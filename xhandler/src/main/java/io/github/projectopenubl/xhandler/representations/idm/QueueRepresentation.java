package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Queue")
public record QueueRepresentation(int priorityClass, long waiting, long unblocked, long running, Instant oldestUnblocked) {}
