package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Sets the archive plan: an exited tenant's data is kept without a deletion date. */
@Schema(name = "ArchivePlanRequest")
public record ArchivePlanRequestRepresentation(Boolean archivePlan) {}
