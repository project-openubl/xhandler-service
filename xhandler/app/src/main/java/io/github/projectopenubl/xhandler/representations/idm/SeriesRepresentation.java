package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SeriesView")
public record SeriesRepresentation(UUID id, String sunatEnv, String tipoComprobante, String serie, long nextNumber, boolean isDefault, boolean active) {}
