package io.github.projectopenubl.xhandler.representations.idm;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Status")
public record StatusRepresentation(
        VersionsRepresentation versions,
        List<WorkersRepresentation> workers,
        boolean database,
        boolean storage,
        boolean keyVault,
        boolean engine,
        List<QueueRepresentation> queues,
        List<SunatEnvironmentRepresentation> sunat) {}
