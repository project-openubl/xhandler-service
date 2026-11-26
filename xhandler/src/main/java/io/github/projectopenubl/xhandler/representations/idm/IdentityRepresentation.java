package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A normalized answer. */
@Schema(name = "Identity")
public record IdentityRepresentation(String tipo, String numero, String nombre, String direccion, String estado, String condicion, boolean cached) {}
