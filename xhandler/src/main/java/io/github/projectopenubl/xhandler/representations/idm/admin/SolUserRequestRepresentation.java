package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A SUNAT SOL user of the platform (the PSE pool); the password is sealed, never returned. */
@Schema(name = "SolUserRequest")
public record SolUserRequestRepresentation(String username, String password, String label) {}
