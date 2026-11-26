package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.ExchangeRateRepresentation;
import io.github.projectopenubl.xhandler.managers.ExchangeRateManager;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** The exchange rate of a day (default tipo_cambio on manual issuing, §26.7). Any signed-in user. */
@Path("/api/v1/exchange-rates")
public class ExchangeRateResource {

    private final ExchangeRateManager rates;

    public ExchangeRateResource(ExchangeRateManager rates) {
        this.rates = rates;
    }

    @Operation(operationId = "getExchangeRate")
    @GET
    @Transactional
    public ExchangeRateRepresentation get(@QueryParam("date") LocalDate date) {
        LocalDate day = date == null ? io.github.projectopenubl.xhandler.utils.Deadlines.todayInLima() : date;
        return rates.on(day).orElseThrow(() -> new ApiException(404, "no_rate", "No hay tipo de cambio registrado para esa fecha."));
    }
}
