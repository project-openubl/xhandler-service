package io.github.projectopenubl.xhandler.representations.idm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "DraftView")
public record DraftRepresentation(
        UUID id,
        String prn,
        UUID companyId,
        @Schema(description = "draft (en edición) o issued (emitido: ver document_id).") String status,
        int version,
        String kind,
        String tipoComprobante,
        String serie,
        JsonNode spec,
        Map<String, String> labels,
        DocumentRepresentation.Customer customer,
        String moneda,
        UUID documentId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {}
