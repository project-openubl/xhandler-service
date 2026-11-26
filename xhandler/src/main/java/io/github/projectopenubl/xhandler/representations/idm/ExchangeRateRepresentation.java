package io.github.projectopenubl.xhandler.representations.idm;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A day's rate. */
@Schema(name = "Rate")
public record ExchangeRateRepresentation(LocalDate day, BigDecimal compra, BigDecimal venta, String source) {}
