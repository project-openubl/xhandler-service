package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.SeriesProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.models.SeriesModel;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.NumberingProvider;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Numbers are assigned by the service inside the emit transaction (DESIGN.md §8) with one atomic
 * update: a number is consumed when its document commits, and reissued if the transaction fails.
 * The unique index on {@code document} is the backstop. The series row stays locked until commit,
 * so emits on one series run one at a time (one series per POS terminal avoids contention).
 */
@ApplicationScoped
public class Numbering {

    private final SeriesProvider seriesProvider;

    /** How far past the series counter a client-chosen number may go. */
    static final long MAX_JUMP = 100_000;

    /** The largest number of a series (8 digits, SUNAT). */
    public static final long MAX_NUMBER = 99_999_999;

    private final NumberingProvider numberingProvider;

    public Numbering(NumberingProvider numberingProvider, SeriesProvider seriesProvider) {
        this.seriesProvider = seriesProvider;
        this.numberingProvider = numberingProvider;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public long reserve(UUID companyId, String sunatEnv, String tipoComprobante, String serie) {
        return numberingProvider.nextSeriesNumber(companyId, sunatEnv, tipoComprobante, serie)
                .orElseThrow(() -> ApiException.invalid("series_not_found",
                        "La serie " + serie + " no existe o no está activa para este tipo de comprobante.", null));
    }

    /**
     * A number the client chose (adapters: UBL upload, SFS, Nubefact clients keep their own
     * numbering). The series is created if it doesn't exist and its counter moves past the number,
     * so later service-numbered emits can't collide. The unique index is the backstop.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public long claim(UUID tenantId, UUID companyId, String sunatEnv, String tipoComprobante, String serie, long numero) {
        if (numero < 1 || numero > MAX_NUMBER) {
            throw ApiException.invalid("invalid_document", "El número debe estar entre 1 y 99999999.", null);
        }
        if (serie == null || !serie.matches("[A-Z0-9]{4}")) {
            throw ApiException.invalid("invalid_document", "La serie debe tener 4 caracteres en mayúsculas o dígitos.", null);
        }
        // Electronic series: F for facturas, B for boletas, T for guías; digits are contingency (physical) series
        String family = switch (tipoComprobante) {
            case "01" -> "F";
            case "03" -> "B";
            case "09" -> "T";
            default -> null;
        };
        if (family != null && !serie.startsWith(family) && !Character.isDigit(serie.charAt(0))) {
            throw ApiException.invalid("invalid_document", "La serie " + serie + " no corresponde al tipo de comprobante " + tipoComprobante + ".", null);
        }
        Optional<NumberingProvider.SeriesCounter> existing =
                numberingProvider.getSeriesCounterForUpdate(companyId, sunatEnv, tipoComprobante, serie);
        if (existing.isPresent()) {
            if (!existing.get().active()) {
                throw ApiException.invalid("series_not_found", "La serie " + serie + " está desactivada.", null);
            }
            long next = existing.get().nextNumber();
            if (numero > next + MAX_JUMP) {
                // One typo would otherwise move the counter for good (up to 99999999)
                throw ApiException.invalid("invalid_document", "El número " + numero + " salta más de " + MAX_JUMP
                        + " números desde el siguiente de la serie (" + next + ").", null);
            }
        }
        numberingProvider.upsertSeriesCounter(io.github.projectopenubl.xhandler.utils.Ids.newId(), tenantId, companyId, sunatEnv,
                tipoComprobante, serie, numero + 1);
        return numero;
    }

    /** RC/RA/RR numbers: {@code RC-YYYYMMDD-n}, per company, environment and Lima day (§8). */
    @Transactional(Transactional.TxType.MANDATORY)
    public int reserveDaily(UUID tenantId, UUID companyId, String sunatEnv, String kind, LocalDate day) {
        return numberingProvider.nextDailyNumber(tenantId, companyId, sunatEnv, kind, day);
    }

    /**
     * The default series for a document type. Notes pick the F or B family of the document they
     * affect ({@code prefix}: "F" or "B").
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public String defaultSerie(UUID companyId, String sunatEnv, String tipoComprobante, String prefix) {
        List<SeriesModel> candidates = seriesProvider.getActiveSeries(companyId, sunatEnv, tipoComprobante);
        return candidates.stream()
                .filter(s -> prefix == null || s.getSerie().startsWith(prefix))
                .findFirst()
                .or(() -> candidates.stream().findFirst())
                .map(s -> s.getSerie())
                .orElseThrow(() -> ApiException.invalid("series_not_found",
                        "La empresa no tiene una serie activa para el tipo de comprobante " + tipoComprobante + ".", null));
    }
}
