package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A created partner. */
@Schema(name = "PartnerCreated")
public record PartnerCreatedRepresentation(UUID id, String name) {}
