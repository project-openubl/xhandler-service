package io.github.projectopenubl.xhandler.models;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Series and daily counters (DESIGN.md §8); every method joins the caller's transaction, which entered the tenant. */
public interface NumberingProvider {

    /** A series row's counter and whether it is active. */
    record SeriesCounter(long nextNumber, boolean active) {}

    /** Consumes the next number of an active series; empty if the series doesn't exist or is inactive. */
    Optional<Long> nextSeriesNumber(UUID companyId, String sunatEnv, String tipoComprobante, String serie);

    /** The series' counter, locking the row until the transaction ends. */
    Optional<SeriesCounter> getSeriesCounterForUpdate(UUID companyId, String sunatEnv, String tipoComprobante, String serie);

    /** Creates the series if missing and moves its counter to at least {@code nextNumber}. */
    void upsertSeriesCounter(UUID id, UUID tenantId, UUID companyId, String sunatEnv, String tipoComprobante, String serie, long nextNumber);

    /** Consumes the next number of a daily counter (RC/RA/RR), creating it at 1. */
    int nextDailyNumber(UUID tenantId, UUID companyId, String sunatEnv, String kind, LocalDate day);

    /** Sets PostgreSQL's {@code lock_timeout} (e.g. {@code "10000ms"}) for the rest of the transaction. */
    void setLockTimeout(String timeout);
}
