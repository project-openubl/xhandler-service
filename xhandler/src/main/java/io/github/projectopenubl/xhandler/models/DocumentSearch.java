package io.github.projectopenubl.xhandler.models;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Filters of a document search; null means no filter. {@code customerNombrePrefix} is a lowercase
 * LIKE pattern, already escaped; {@code labels} must all match.
 */
public record DocumentSearch(
        UUID companyId,
        String status,
        String tipoComprobante,
        String serie,
        String holdReason,
        String customerNumero,
        String customerNombrePrefix,
        LocalDate from,
        LocalDate to,
        Map<String, String> labels) {}
