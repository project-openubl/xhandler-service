package io.github.projectopenubl.xhandler.representations.idm;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** An export: {@code parts} lists the ZIPs to download (the index first), once {@code done}. */
@Schema(name = "ExportView")
public record ExportRepresentation(UUID id, String status, List<ExportPartRepresentation> parts, Integer documents, String detail, String createdAt,
        String doneAt) {}
