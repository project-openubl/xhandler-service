package io.github.projectopenubl.xhandler.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Daily exchange rates (DESIGN.md §26.7), platform data; every method joins the caller's transaction. */
public interface ExchangeRateProvider {

    /** A stored day's rate. */
    record ExchangeRate(LocalDate day, BigDecimal compra, BigDecimal venta, String source) {}

    /** The latest rate on or before {@code day} and after {@code oldest}. */
    Optional<ExchangeRate> getLatestRate(LocalDate day, LocalDate oldest);

    /** Stores a day's rate, replacing the one stored for that day. */
    void putRate(LocalDate day, BigDecimal compra, BigDecimal venta, String source);
}
