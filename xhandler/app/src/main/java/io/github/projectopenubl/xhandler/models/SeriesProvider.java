package io.github.projectopenubl.xhandler.models;

import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link SeriesModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface SeriesProvider {

    /** Creates a series with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted series with {@code id}; see {@link #persist}. */
    SeriesModel newSeries(UUID id);

    void persist(SeriesModel series);

    void delete(SeriesModel series);

    SeriesModel addSeries(UUID id, Consumer<SeriesModel> init);

    Optional<SeriesModel> getSeries(UUID id);

    /** Same as {@link #getSeries}, locking the row until the transaction ends. */
    Optional<SeriesModel> getSeriesForUpdate(UUID id);

    /** Same as {@link #getSeries}, share-locking the row until the transaction ends. */
    Optional<SeriesModel> getSeriesForShare(UUID id);

    /** Re-reads the series from the database (after native updates that bypass the session). */
    void refresh(SeriesModel series);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    /** Active series of a type, default ones first. */
    List<SeriesModel> getActiveSeries(UUID companyId, String sunatEnv, String tipoComprobante);

    List<SeriesModel> getSeries(UUID companyId, String sunatEnv);

    boolean seriesExists(UUID companyId, UUID seriesId);

    boolean seriesExists(UUID companyId, String sunatEnv, String tipoComprobante, String serie);

    /**
     * Moves the series counter forward to {@code nextNumber} (never back), bypassing the session;
     * returns how many rows changed.
     */
    int moveNextNumber(UUID seriesId, long nextNumber);

    /** Activates or deactivates the series, bypassing the session. */
    void setActive(UUID seriesId, boolean active);
}
