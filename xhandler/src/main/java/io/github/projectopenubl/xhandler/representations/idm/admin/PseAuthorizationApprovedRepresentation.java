package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** An approved PSE authorization. */
@Schema(name = "PseAuthorizationApproved")
public record PseAuthorizationApprovedRepresentation(UUID id, boolean approved) {}
