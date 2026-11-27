package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Sets whether a company must send through an OSE (§3.2). */
@Schema(name = "OseObligatedRequest")
public record OseObligatedRequestRepresentation(Boolean oseObligated) {}
