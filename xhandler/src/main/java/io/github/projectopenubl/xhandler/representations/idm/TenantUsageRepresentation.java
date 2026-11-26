package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TenantUsage")
public record TenantUsageRepresentation(UUID id, String name, String status, String plan, long productionDocuments, long otherDocuments) {}
