package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PartnerTenant")
public record PartnerTenantRepresentation(UUID id, String name, UUID partnerId) {}
