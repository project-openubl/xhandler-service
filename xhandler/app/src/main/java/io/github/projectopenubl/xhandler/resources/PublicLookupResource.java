package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.OseLookupResultRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.OseLookupRequestRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.LookupResultRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.LookupRequestRepresentation;
import io.github.projectopenubl.xhandler.utils.PublicStatus;
import io.github.projectopenubl.xhandler.models.PublicLookupProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * Lookup by printed data (DESIGN.md §12): RUC + serie-número + date + total is low-entropy, so it
 * requires a CAPTCHA, is limited per RUC (and per IP at the gateway), and answers only the status,
 * never the files. Production documents only.
 */
@Path("/api/v1/public/lookup")
public class PublicLookupResource {




    private final PublicLookupProvider lookups;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Optional<String> verifyUrl;
    private final Optional<String> secret;
    private final boolean testMode;
    private final int perRucPerHour;

    public PublicLookupResource(PublicLookupProvider lookups,
            @ConfigProperty(name = "xhandler.captcha.verify-url") Optional<String> verifyUrl,
            @ConfigProperty(name = "xhandler.captcha.secret") Optional<String> secret,
            @ConfigProperty(name = "xhandler.captcha.test-mode", defaultValue = "false") boolean testMode,
            @ConfigProperty(name = "xhandler.public-lookup.per-ruc-per-hour", defaultValue = "300") int perRucPerHour) {
        this.perRucPerHour = perRucPerHour;
        this.lookups = lookups;
        this.verifyUrl = verifyUrl;
        this.secret = secret;
        this.testMode = testMode;
    }

    @Operation(operationId = "lookupDocument")
    @POST
    public LookupResultRepresentation lookup(@Valid LookupRequestRepresentation request) {
        if (!captchaValid(request.captcha())) {
            throw new ApiException(403, "captcha_failed", "Verificación CAPTCHA inválida.");
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            int hits = lookups.countLookup("ruc:" + request.ruc());
            if (hits > perRucPerHour) {
                throw new ApiException(429, "too_many_requests", "Demasiadas consultas para este RUC. Intente más tarde.");
            }
            String status = lookups.getPublicDocumentStatus(request.ruc(), request.tipoComprobante(), request.serie(), request.numero(),
                    request.fechaEmision(), request.total());
            return new LookupResultRepresentation(status == null ? "no_encontrado" : PublicStatus.of(status));
        });
    }



    /**
     * The OSE's public validity query (RS 242-2018, RS 117-2017 art. 7.15): during the retention
     * period, whether a factura or nota got a CDR-OSE or an inconsistency notice, and for boletas
     * whether and when they were checked. Status only.
     */
    @Operation(operationId = "lookupOseDocument")
    @POST
    @jakarta.ws.rs.Path("/ose")
    public OseLookupResultRepresentation ose(@Valid OseLookupRequestRepresentation request) {
        if (!captchaValid(request.captcha())) {
            throw new ApiException(403, "captcha_failed", "Verificación CAPTCHA inválida.");
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            int hits = lookups.countLookup("ose:" + request.ruc());
            if (hits > perRucPerHour) {
                throw new ApiException(429, "too_many_requests", "Demasiadas consultas para este RUC. Intente más tarde.");
            }
            Optional<PublicLookupProvider.OseSubmission> rows =
                    lookups.getOseSubmission(request.ruc(), request.tipoComprobante(), request.serie(), request.numero());
            if (rows.isEmpty()) {
                return new OseLookupResultRepresentation("no_encontrado", null);
            }
            PublicLookupProvider.OseSubmission r = rows.get();
            boolean cdr = "cdr".equals(r.result());
            return new OseLookupResultRepresentation(cdr ? "con_cdr" : "con_inconsistencia",
                    String.valueOf(cdr ? r.cdrIssuedAt() : r.receivedAt()));
        });
    }

    /** hCaptcha/Turnstile-style verification (POST secret + response, JSON {"success": true}). */
    private boolean captchaValid(String token) {
        if (testMode) {
            return "test-pass".equals(token);
        }
        if (verifyUrl.isEmpty() || secret.isEmpty()) {
            return false;
        }
        try {
            String form = "secret=" + URLEncoder.encode(secret.get(), StandardCharsets.UTF_8) + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(verifyUrl.get())).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().replace(" ", "").contains("\"success\":true");
        } catch (java.io.IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
