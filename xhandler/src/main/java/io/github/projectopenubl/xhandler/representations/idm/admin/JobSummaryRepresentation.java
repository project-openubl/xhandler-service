package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Any job of the platform, whichever tenant it belongs to. */
@Schema(name = "JobSummary")
public record JobSummaryRepresentation(UUID id, String name, String state, String worker, String error) {}
