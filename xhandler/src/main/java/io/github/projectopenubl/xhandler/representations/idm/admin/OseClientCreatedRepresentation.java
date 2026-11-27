package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A new OSE user; the password is shown only in this answer. */
@Schema(name = "OseClientCreated")
public record OseClientCreatedRepresentation(UUID id, String issuerRuc, String username, String password) {}
