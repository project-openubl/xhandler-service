package io.github.projectopenubl.xhandler.representations.idm;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ConformityView")
public record ConformityRepresentation(
        UUID documentId, String serieNumero, String buyerRuc, String state, Instant acceptedAt, Instant windowEndsAt, Instant checkedAt,
        String checkDetail, Instant resolvedAt, Map<String, Object> suggestedCreditNote) {}
