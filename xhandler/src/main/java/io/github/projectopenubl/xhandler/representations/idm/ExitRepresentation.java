package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The tenant after its exit: deleted after {@code deleteAfter}, never when it has an archive plan. */
@Schema(name = "ExitView")
public record ExitRepresentation(String status, String deleteAfter) {}
