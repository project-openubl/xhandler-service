package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.PublicLookupProvider;
import io.github.projectopenubl.xhandler.managers.CompanyManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.resources.CatalogResource;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.resources.CompanyResource;
import io.github.projectopenubl.xhandler.managers.CredentialService;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.github.projectopenubl.xhandler.clients.Egress;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * RUC/DNI lookup behind one interface, {@code lookup(tipo, numero)} (DESIGN.md §17): the platform's
 * searchpe, or a commercial provider with the tenant's token (credential kind {@code lookup}:
 * {@code url} with {@code {tipo}} and {@code {numero}} placeholders, and {@code token}). Results are
 * cached in the company's customer catalog. Provider URLs are tenant-supplied: egress rules apply.
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/lookup/{tipo}/{numero}")
public class IdentityLookup {

    private final PublicLookupProvider publicLookupProvider;

    private final CompanyManager companyManager;

    /** A normalized answer. */
    public record Identity(String tipo, String numero, String nombre, String direccion, String estado, String condicion, boolean cached) {}

    private final TenantAccess access;
    private final CredentialService credentials;
    private final CatalogResource catalogs;
    private final Egress egress;
    private final ObjectMapper json;
    private final Optional<String> searchpeUrl;
    private final io.github.projectopenubl.xhandler.managers.Auditor auditor;
    private final io.github.projectopenubl.xhandler.models.CatalogProvider catalogProvider;
    private final int maxPerHour;

    static final java.time.Duration CACHE_TTL = java.time.Duration.ofDays(30);

    public IdentityLookup(TenantAccess access, CredentialService credentials, CatalogResource catalogs, Egress egress, ObjectMapper json,
            @ConfigProperty(name = "xhandler.lookup.searchpe-url") Optional<String> searchpeUrl,
            io.github.projectopenubl.xhandler.managers.Auditor auditor, io.github.projectopenubl.xhandler.models.CatalogProvider catalogProvider,
            @ConfigProperty(name = "xhandler.lookup.max-per-tenant-per-hour", defaultValue = "300") int maxPerHour, CompanyManager companyManager, PublicLookupProvider publicLookupProvider) {
        this.publicLookupProvider = publicLookupProvider;
        this.companyManager = companyManager;
        this.auditor = auditor;
        this.catalogProvider = catalogProvider;
        this.maxPerHour = maxPerHour;
        this.access = access;
        this.credentials = credentials;
        this.catalogs = catalogs;
        this.egress = egress;
        this.json = json;
        this.searchpeUrl = searchpeUrl;
    }

    @Operation(operationId = "lookupIdentity")
    @GET
    public Identity lookup(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("tipo") String tipo,
            @PathParam("numero") String numero) {
        String catalogTipo = switch (tipo) {
            case "ruc" -> {
                if (!numero.matches("[12]\\d{10}")) {
                    throw ApiException.invalid("invalid_request", "El RUC debe tener 11 dígitos.", null);
                }
                yield "6";
            }
            case "dni" -> {
                if (!numero.matches("\\d{8}")) {
                    throw ApiException.invalid("invalid_request", "El DNI debe tener 8 dígitos.", null);
                }
                yield "1";
            }
            default -> throw ApiException.notFound();
        };
        record Provider(String url, String token, boolean tenantSupplied) {}
        Object cachedOrProvider = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.OPERATOR);
            CompanyModel company = companyManager.getCompany(companyId);
            // Personal data: every lookup is audited, and limited per tenant (no free DNI → name oracle)
            auditor.record(scope, "lookup.identity", "company", companyId, Map.of("tipo", tipo, "numero", numero));
            int hits = publicLookupProvider.countLookup("lookup:" + tenantId);
            if (hits > maxPerHour) {
                throw new ApiException(429, "too_many_requests", "Demasiadas consultas de RUC/DNI. Intente en una hora.");
            }
            Optional<CatalogResource.Entry> cached = catalogs.byKey(companyId, "customer", catalogTipo + ":" + numero);
            if (cached.isPresent() && cached.get().data().has("lookup_at")
                    && java.time.Instant.parse(cached.get().data().get("lookup_at").asText()).isAfter(java.time.Instant.now().minus(CACHE_TTL))) {
                JsonNode d = cached.get().data();
                return new Identity(tipo, numero, d.path("nombre").asText(), text(d, "direccion"), text(d, "estado"), text(d, "condicion"), true);
            }
            Optional<Map<String, String>> own = credentials.read(scope, company, "lookup", "lookup");
            if (own.isPresent()) {
                return new Provider(own.get().get("url"), own.get().get("token"), true);
            }
            return searchpeUrl.map(url -> new Provider(url, null, false)).orElseThrow(() ->
                    new ApiException(503, "lookup_unavailable", "La consulta de RUC/DNI no está configurada."));
        });
        if (cachedOrProvider instanceof Identity identity) {
            return identity;
        }
        Provider provider = (Provider) cachedOrProvider;
        // Only validated values reach the provider URL: the type is a constant and the number is
        // rebuilt from its digits, so no text from the request can change the host (CodeQL java/ssrf)
        boolean ruc = "ruc".equals(tipo);
        String urlTipo = ruc ? "ruc" : "dni";
        String urlNumero = String.format(ruc ? "%011d" : "%08d", Long.parseLong(numero));
        String url = provider.url().replace("{tipo}", urlTipo).replace("{numero}", urlNumero);
        if (provider.tenantSupplied()) {
            egress.check(url);
        }
        JsonNode answer;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET();
            if (provider.token() != null) {
                request.header("Authorization", "Bearer " + provider.token());
            }
            HttpResponse<String> response = egress.client().send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw ApiException.notFound();
            }
            if (response.statusCode() / 100 != 2) {
                throw new ApiException(502, "lookup_failed", "El servicio de consulta respondió con error.");
            }
            answer = json.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new ApiException(502, "lookup_failed", "No se pudo consultar el servicio de RUC/DNI.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "lookup_unavailable", "Consulta interrumpida.");
        }
        // searchpe and the usual commercial providers answer with these names (or their Spanish synonyms)
        String nombre = first(answer, "nombre", "razonSocial", "razon_social", "nombreCompleto");
        if (nombre == null) {
            throw ApiException.notFound();
        }
        Identity identity = new Identity(tipo, numero, nombre, first(answer, "direccion", "domicilioFiscal"), first(answer, "estado", "estadoContribuyente"),
                first(answer, "condicion", "condicionDomicilio"), false);
        QuarkusTransaction.requiringNew().run(() -> {
            access.enter(tenantId, Role.OPERATOR);
            Optional<CatalogResource.Entry> existing = catalogs.byKey(companyId, "customer", catalogTipo + ":" + numero);
            // Merge: fields the user entered (contact, email...) are kept; only the looked-up ones change
            ObjectNode data = existing.map(e -> (ObjectNode) e.data().deepCopy()).orElseGet(json::createObjectNode);
            data.put("tipo_documento_identidad", catalogTipo);
            data.put("numero_documento_identidad", numero);
            data.put("nombre", identity.nombre());
            if (identity.direccion() != null) {
                data.put("direccion", identity.direccion());
            }
            if (identity.estado() != null) {
                data.put("estado", identity.estado());
            }
            if (identity.condicion() != null) {
                data.put("condicion", identity.condicion());
            }
            data.put("lookup_at", java.time.Instant.now().toString());
            catalogs.upsert(tenantId, companyId, "customer", data, existing.map(CatalogResource.Entry::id).orElse(null));
        });
        return identity;
    }

    private static String first(JsonNode node, String... names) {
        for (String name : names) {
            if (node.hasNonNull(name) && !node.get(name).asText().isBlank()) {
                return node.get(name).asText();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String name) {
        return node.hasNonNull(name) ? node.get(name).asText() : null;
    }
}
