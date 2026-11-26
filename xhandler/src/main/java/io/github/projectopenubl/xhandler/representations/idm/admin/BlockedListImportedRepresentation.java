package io.github.projectopenubl.xhandler.representations.idm.admin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** How many issuers a blocked list import stored. */
@Schema(name = "BlockedListImported")
public record BlockedListImportedRepresentation(String list, int imported) {}
