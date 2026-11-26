package io.github.projectopenubl.xhandler.representations.idm;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ReceptionView")
public record ReceptionRepresentation(UUID id, String supplierRuc, String tipoComprobante, String serieNumero, LocalDate fechaEmision, String moneda,
        BigDecimal total, String validityState, Instant validityCheckedAt, boolean hasPdf, boolean hasCdr, Instant createdAt) {}
