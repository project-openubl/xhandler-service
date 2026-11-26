package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PartnerView")
public record PartnerRepresentation(UUID id, String name, String role) {}
