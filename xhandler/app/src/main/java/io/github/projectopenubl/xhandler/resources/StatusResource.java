package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.StatusProvider;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.keys.KeyVault;
import io.github.projectopenubl.xhandler.files.FileStore;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Platform status (DESIGN.md §15), like Pulp's: versions, online workers per role, database,
 * storage and key vault health, and queue depth. Public (rate limited at the gateway), and free of
 * tenant data and pod names.
 */
@Path("/api/v1/status")
public class StatusResource {

    public record Versions(String service, String engine, String xhandlerRust) {}

    public record Workers(String role, long online) {}

    public record Queue(int priorityClass, long waiting, long unblocked, long running, Instant oldestUnblocked) {}

    /** One SUNAT web service of an environment, as its circuit breaker sees it (DESIGN.md §24). */
    public record SunatEndpoint(
            @Schema(description = "Servicio de SUNAT: invoice (comprobantes y resúmenes), despatch (guías) o perception (percepciones y retenciones).") String service,
            @Schema(description = "closed (responde), open (caído: no se le envía) o half_open (probando si volvió).") String breakerState,
            @Schema(description = "Hasta cuándo queda abierto el disyuntor; luego se prueba de nuevo.") Instant breakerUntil,
            @Schema(description = "Pausado por la plataforma: no se le envía.") boolean paused) {}

    /**
     * SUNAT availability per environment, from the breakers of its services. {@code available}: every
     * service seen so far answers (breaker closed, not paused); an environment nothing was sent to
     * yet has no services and counts as available.
     */
    public record SunatEnvironment(
            @Schema(description = "production, beta o fake.") String environment,
            boolean available,
            List<SunatEndpoint> endpoints) {}

    public record Status(
            Versions versions,
            List<Workers> workers,
            boolean database,
            boolean storage,
            boolean keyVault,
            boolean engine,
            List<Queue> queues,
            List<SunatEnvironment> sunat) {}

    static final List<String> ENVIRONMENTS = List.of("production", "beta", "fake");

    private final StatusProvider statusProvider;
    private final Engine engine;
    private final FileStore files;
    private final KeyVault vault;
    private final String version;

    public StatusResource(
            StatusProvider statusProvider,
            Engine engine,
            FileStore files,
            KeyVault vault,
            @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev") String version) {
        this.statusProvider = statusProvider;
        this.engine = engine;
        this.files = files;
        this.vault = vault;
        this.version = version;
    }

    @Operation(operationId = "getStatus")
    @GET
    @Transactional
    public Status status() {
        List<StatusProvider.WorkerCount> workers = statusProvider.getOnlineWorkers();
        List<StatusProvider.QueueStats> queues = statusProvider.getQueueStats();
        EngineModels.InfoResponse info = null;
        try {
            info = engine.info();
        } catch (RuntimeException e) {
            // Reported as engine: false
        }
        boolean vaultOk;
        try {
            vaultOk = vault.currentMasterKeyId() != null;
        } catch (RuntimeException e) {
            vaultOk = false;
        }
        return new Status(
                new Versions(version, info == null ? null : info.engineVersion(), info == null ? null : info.xhandlerRustRev()),
                workers.stream().map(w -> new Workers(w.role(), w.online())).toList(),
                true,
                files.healthy(),
                vaultOk,
                info != null,
                queues.stream().map(q -> new Queue(q.priorityClass(), q.waiting(), q.unblocked(), q.running(), q.oldestUnblocked())).toList(),
                sunat());
    }

    /** SUNAT's endpoints only: a third-party OSE ({@code ose:…}) is no platform-wide status. */
    private List<SunatEnvironment> sunat() {
        List<StatusProvider.EndpointState> rows = statusProvider.getSunatEndpoints();
        return ENVIRONMENTS.stream().map(env -> {
            List<SunatEndpoint> endpoints = rows.stream()
                    .filter(r -> r.id().startsWith(env + ":"))
                    .map(r -> new SunatEndpoint(r.id().substring(env.length() + 1), r.breakerState(), r.breakerUntil(), r.paused()))
                    .toList();
            boolean available = endpoints.stream().allMatch(e -> "closed".equals(e.breakerState()) && !e.paused());
            return new SunatEnvironment(env, available, endpoints);
        }).toList();
    }

}
