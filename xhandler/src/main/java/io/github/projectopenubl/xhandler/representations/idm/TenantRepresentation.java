package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TenantView")
public record TenantRepresentation(UUID id, String name, String status, int retentionYears) {}
