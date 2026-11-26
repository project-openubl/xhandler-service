package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** An address the platform's email provider reported (hard bounce or complaint, §26.5). */
@Schema(name = "EmailSuppression")
public record EmailSuppressionRepresentation(String address, String reason) {}
