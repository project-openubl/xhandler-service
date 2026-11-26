package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.representations.idm.ExchangeRateRepresentation;
import io.github.projectopenubl.xhandler.models.ExchangeRateProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Imports the day's SUNAT/SBS exchange rate (DESIGN.md §26.7, scheduled). The source is SUNAT's
 * public file ({@code dd/MM/yyyy|compra|venta|}), configurable.
 */
@ApplicationScoped
public class ExchangeRatesJob implements JobHandler {

    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExchangeRateProvider rates;
    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ExchangeRatesJob(ExchangeRateProvider rates,
            @ConfigProperty(name = "xhandler.exchange-rates.url", defaultValue = "https://www.sunat.gob.pe/a/txt/tipoCambio.txt") String url) {
        this.rates = rates;
        this.url = url;
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
        ExchangeRateRepresentation rate = parse(response.body().strip(), url);
        QuarkusTransaction.requiringNew().run(() -> rates.putRate(rate.day(), rate.compra(), rate.venta(), rate.source()));
        return Map.of("day", rate.day().toString(), "compra", rate.compra().toPlainString(), "venta", rate.venta().toPlainString());
    }

    /** {@code 16/11/2026|3.380|3.390|} */
    static ExchangeRateRepresentation parse(String line, String source) {
        String[] parts = line.split("\\|");
        if (parts.length < 3) {
            throw new IllegalArgumentException("unexpected exchange rate line: " + line);
        }
        ExchangeRateRepresentation rate = new ExchangeRateRepresentation(LocalDate.parse(parts[0].strip(), DMY), new BigDecimal(parts[1].strip()), new BigDecimal(parts[2].strip()), source);
        BigDecimal low = BigDecimal.ONE;
        BigDecimal high = BigDecimal.TEN;
        if (rate.day().isAfter(LocalDate.now().plusDays(1)) || rate.compra().compareTo(low) < 0 || rate.venta().compareTo(low) < 0
                || rate.compra().compareTo(high) > 0 || rate.venta().compareTo(high) > 0) {
            throw new IllegalArgumentException("implausible exchange rate: " + line);
        }
        return rate;
    }
}
