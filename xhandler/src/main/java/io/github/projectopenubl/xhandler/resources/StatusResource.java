package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.WorkersRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.VersionsRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SunatEnvironmentRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.SunatEndpointRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.StatusRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.QueueRepresentation;
import io.github.projectopenubl.xhandler.models.StatusProvider;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.keys.KeyVault;
import io.github.projectopenubl.xhandler.files.FileStore;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * Platform status (DESIGN.md §15), like Pulp's: versions, online workers per role, database,
 * storage and key vault health, and queue depth. Public (rate limited at the gateway), and free of
 * tenant data and pod names.
 */
@Path("/api/v1/status")
public class StatusResource {







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
    public StatusRepresentation status() {
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
        return new StatusRepresentation(
                new VersionsRepresentation(version, info == null ? null : info.engineVersion(), info == null ? null : info.xhandlerRustRev()),
                workers.stream().map(w -> new WorkersRepresentation(w.role(), w.online())).toList(),
                true,
                files.healthy(),
                vaultOk,
                info != null,
                queues.stream().map(q -> new QueueRepresentation(q.priorityClass(), q.waiting(), q.unblocked(), q.running(), q.oldestUnblocked())).toList(),
                sunat());
    }

    private List<SunatEnvironmentRepresentation> sunat() {
        List<StatusProvider.EndpointState> rows = statusProvider.getSunatEndpoints();
        return ENVIRONMENTS.stream().map(env -> {
            List<SunatEndpointRepresentation> endpoints = rows.stream()
                    .filter(r -> r.id().startsWith(env + ":"))
                    .map(r -> new SunatEndpointRepresentation(r.id().substring(env.length() + 1), r.breakerState(), r.breakerUntil(), r.paused()))
                    .toList();
            boolean available = endpoints.stream().allMatch(e -> "closed".equals(e.breakerState()) && !e.paused());
            return new SunatEnvironmentRepresentation(env, available, endpoints);
        }).toList();
    }

}
