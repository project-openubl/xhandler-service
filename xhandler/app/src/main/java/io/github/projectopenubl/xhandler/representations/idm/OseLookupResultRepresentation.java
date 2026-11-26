package io.github.projectopenubl.xhandler.representations.idm;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** {@code state}: con_cdr, con_inconsistencia, no_encontrado; {@code checked_at}: when the OSE checked it. */
@Schema(name = "OseLookupResult")
public record OseLookupResultRepresentation(String state, String checkedAt) {}
