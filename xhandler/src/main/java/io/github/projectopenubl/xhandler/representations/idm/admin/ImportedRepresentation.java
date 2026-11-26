package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** How many entries an import stored. */
@Schema(name = "Imported")
public record ImportedRepresentation(int imported) {}
