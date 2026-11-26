package io.github.projectopenubl.xhandler.managers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.ReportProvider;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one row generator behind CSV, Excel, SIRE and legacy PLE (DESIGN.md §11.9): the sales
 * register from the company's documents, the purchases register from its receptions. Credit notes
 * carry negative amounts; voided documents keep their row with zero amounts (estado anulado).
 */
@ApplicationScoped
public class RegisterRows {

    /** Amount columns, in register order. */
    public static final List<String> AMOUNTS =
            List.of("exportacion", "gravada", "igv", "exonerada", "inafecta", "isc", "ivap_base", "ivap", "icbper", "otros");

    /**
     * One row of a register. {@code party} is the buyer (sales) or the supplier (purchases);
     * {@code affected*} the document a note modifies.
     */
    public record Row(
            LocalDate fecha,
            String tipo,
            String serie,
            long numero,
            String partyTipo,
            String partyNumero,
            String partyNombre,
            String moneda,
            BigDecimal tipoCambio,
            Map<String, BigDecimal> amounts,
            BigDecimal total,
            boolean voided,
            LocalDate affectedFecha,
            String affectedTipo,
            String affectedSerie,
            Long affectedNumero,
            boolean amountsMissing) {

        public String key() {
            return tipo + "-" + serie + "-" + numero;
        }

        public BigDecimal amount(String name) {
            return amounts.getOrDefault(name, BigDecimal.ZERO);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {};

    private final ReportProvider reportProvider;

    public RegisterRows(ReportProvider reportProvider) {
        this.reportProvider = reportProvider;
    }

    /** With the tenant open. {@code period} is YYYYMM. */
    @Transactional(Transactional.TxType.MANDATORY)
    public List<Row> sales(UUID companyId, String sunatEnv, String period) {
        YearMonth month = month(period);
        List<ReportProvider.SalesRow> rows = reportProvider.getSalesRows(companyId, sunatEnv, month.atDay(1), month.atEndOfMonth());
        List<Row> out = new ArrayList<>(rows.size());
        for (ReportProvider.SalesRow r : rows) {
            String tipo = r.tipo();
            boolean voided = "voided".equals(r.status());
            out.add(row(r.fecha(), tipo, r.serie(), r.numero(), r.customerTipo(), r.customerNumero(), r.customerNombre(),
                    r.moneda(), r.tipoCambio(), r.amounts(), r.total(), voided,
                    r.affectedFecha(), r.affectedTipo(), r.affectedSerie(), r.affectedNumero()));
        }
        return out;
    }

    /** Receptions SUNAT didn't report as voided, rejected or nonexistent. */
    @Transactional(Transactional.TxType.MANDATORY)
    public List<Row> purchases(UUID companyId, String period) {
        YearMonth month = month(period);
        List<ReportProvider.PurchaseRow> rows = reportProvider.getPurchaseRows(companyId, month.atDay(1), month.atEndOfMonth());
        List<Row> out = new ArrayList<>(rows.size());
        for (ReportProvider.PurchaseRow r : rows) {
            String affected = r.affectedSerieNumero();
            String affectedSerie = null;
            Long affectedNumero = null;
            if (affected != null && affected.contains("-")) {
                affectedSerie = affected.substring(0, affected.lastIndexOf('-'));
                try {
                    affectedNumero = Long.parseLong(affected.substring(affected.lastIndexOf('-') + 1));
                } catch (NumberFormatException e) {
                    affectedNumero = null;
                }
            }
            out.add(row(r.fecha(), r.tipo(), r.serie(), r.numero(), "6", r.supplierRuc(), r.supplierName(),
                    r.moneda(), r.tipoCambio(), r.amounts(), r.total(), false, null, r.affectedTipo(), affectedSerie,
                    affectedNumero));
        }
        return out;
    }

    private static Row row(LocalDate fecha, String tipo, String serie, long numero, String partyTipo, String partyNumero,
            String partyNombre, String moneda, BigDecimal tipoCambio, String amountsJson, BigDecimal total, boolean voided,
            LocalDate affectedFecha, String affectedTipo, String affectedSerie, Long affectedNumero) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        BigDecimal sign = "07".equals(tipo) ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        boolean missing = !voided && (amountsJson == null || amountsJson.contains("\"_error\""));
        if (!voided && !missing) {
            try {
                for (Map.Entry<String, String> e : JSON.readValue(amountsJson, MAP).entrySet()) {
                    if (AMOUNTS.contains(e.getKey())) {
                        amounts.put(e.getKey(), new BigDecimal(e.getValue()).multiply(sign));
                    }
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException | NumberFormatException e) {
                throw new IllegalStateException("unreadable amounts", e);
            }
        }
        BigDecimal signedTotal = voided || total == null ? BigDecimal.ZERO : total.multiply(sign);
        return new Row(fecha, tipo, serie, numero, partyTipo, partyNumero, partyNombre, moneda == null ? "PEN" : moneda, tipoCambio,
                amounts, signedTotal, voided, affectedFecha, affectedTipo, affectedSerie, affectedNumero, missing);
    }

    /**
     * Documents of the period without a final answer yet (or stuck in exception): a register built
     * now would leave them out.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public long unresolved(UUID companyId, String sunatEnv, String period) {
        YearMonth month = month(period);
        return reportProvider.countUnresolvedDocuments(companyId, sunatEnv, month.atDay(1), month.atEndOfMonth());
    }

    public static YearMonth month(String period) {
        if (period == null || !period.matches("\\d{4}(0[1-9]|1[0-2])")) {
            throw io.github.projectopenubl.xhandler.resources.exceptions.ApiException.invalid("invalid_request", "El periodo debe tener el formato AAAAMM.",
                    null);
        }
        return YearMonth.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(4)));
    }
}
