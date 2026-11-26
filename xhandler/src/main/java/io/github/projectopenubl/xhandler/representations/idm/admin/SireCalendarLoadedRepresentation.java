package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** How many SIRE calendar entries were stored. */
@Schema(name = "SireCalendarLoaded")
public record SireCalendarLoadedRepresentation(int loaded) {}
