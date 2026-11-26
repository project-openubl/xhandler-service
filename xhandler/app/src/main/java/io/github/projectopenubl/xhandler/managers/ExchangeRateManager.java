package io.github.projectopenubl.xhandler.managers;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.ExchangeRateProvider;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The daily SUNAT/SBS exchange rate (DESIGN.md §26.7): imported by a job, used for the S/ 700
 * boleta check in foreign currency, PEN equivalents in reports, and the default tipo_cambio on
 * manual issuing. A day without a published rate (weekends, holidays) uses the latest earlier one.
 *
 * <p>{@link io.github.projectopenubl.xhandler.jobs.handlers.ExchangeRatesJob} imports them.
 */
@ApplicationScoped
public class ExchangeRateManager {

    /** A day's rate. */
    public record Rate(LocalDate day, BigDecimal compra, BigDecimal venta, String source) {}

    /** An older rate means the import is broken: better no rate than a stale one. */
    static final int MAX_AGE_DAYS = 10;

    private final ExchangeRateProvider rates;

    public ExchangeRateManager(ExchangeRateProvider rates) {
        this.rates = rates;
    }

    /** The rate for a day, or the latest earlier one. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Rate> on(LocalDate day) {
        return rates.getLatestRate(day, day.minusDays(MAX_AGE_DAYS)).map(r -> new Rate(r.day(), r.compra(), r.venta(), r.source()));
    }
}
