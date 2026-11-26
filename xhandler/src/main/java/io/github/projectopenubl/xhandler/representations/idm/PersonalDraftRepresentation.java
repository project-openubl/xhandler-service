package io.github.projectopenubl.xhandler.representations.idm;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A draft naming the person as buyer; deleted ones too, until they are purged. */
@Schema(name = "PersonalDraft")
public record PersonalDraftRepresentation(UUID id, String issuerRuc, String kind, String tipoComprobante, String serie, String nombre, String status,
        String createdAt, String deletedAt) {}
