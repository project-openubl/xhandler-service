package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.ExchangeRateProvider;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The daily SUNAT/SBS exchange rate (DESIGN.md §26.7): imported by a job, used for the S/ 700
 * boleta check in foreign currency, PEN equivalents in reports, and the default tipo_cambio on
 * manual issuing. A day without a published rate (weekends, holidays) uses the latest earlier one.
 *
 * <p>The source is SUNAT's public file ({@code dd/MM/yyyy|compra|venta|}), configurable.
 */
@ApplicationScoped
public class ExchangeRates implements JobHandler {

    /** A day's rate. */
    public record Rate(LocalDate day, BigDecimal compra, BigDecimal venta, String source) {}

    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** An older rate means the import is broken: better no rate than a stale one. */
    static final int MAX_AGE_DAYS = 10;

    private final ExchangeRateProvider rates;
    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ExchangeRates(ExchangeRateProvider rates,
            @ConfigProperty(name = "xhandler.exchange-rates.url", defaultValue = "https://www.sunat.gob.pe/a/txt/tipoCambio.txt") String url) {
        this.rates = rates;
        this.url = url;
    }

    /** The rate for a day, or the latest earlier one. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Rate> on(LocalDate day) {
        return rates.getLatestRate(day, day.minusDays(MAX_AGE_DAYS)).map(r -> new Rate(r.day(), r.compra(), r.venta(), r.source()));
    }

    @Override
    public String name() {
        return "platform.exchange-rates";
    }

    @Override
    public Map<String, Object> run(JobContext context) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("exchange rate source answered HTTP " + response.statusCode());
        }
        Rate rate = parse(response.body().strip(), url);
        QuarkusTransaction.requiringNew().run(() -> rates.putRate(rate.day(), rate.compra(), rate.venta(), rate.source()));
        return Map.of("day", rate.day().toString(), "compra", rate.compra().toPlainString(), "venta", rate.venta().toPlainString());
    }

    /** {@code 16/11/2026|3.380|3.390|} */
    static Rate parse(String line, String source) {
        String[] parts = line.split("\\|");
        if (parts.length < 3) {
            throw new IllegalArgumentException("unexpected exchange rate line: " + line);
        }
        Rate rate = new Rate(LocalDate.parse(parts[0].strip(), DMY), new BigDecimal(parts[1].strip()), new BigDecimal(parts[2].strip()), source);
        BigDecimal low = BigDecimal.ONE;
        BigDecimal high = BigDecimal.TEN;
        if (rate.day().isAfter(LocalDate.now().plusDays(1)) || rate.compra().compareTo(low) < 0 || rate.venta().compareTo(low) < 0
                || rate.compra().compareTo(high) > 0 || rate.venta().compareTo(high) > 0) {
            throw new IllegalArgumentException("implausible exchange rate: " + line);
        }
        return rate;
    }
}
