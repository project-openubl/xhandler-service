package io.github.projectopenubl.xhandler.managers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.IdempotencyProvider;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code Idempotency-Key} on creates (DESIGN.md §11.1). The first request inserts the key in its
 * own committed transaction, so concurrent retries see it:
 *
 * <ul>
 *   <li>same hash and a stored response: the stored response is returned;
 *   <li>same key while the first request runs: 409;
 *   <li>a different request body: 422.
 * </ul>
 *
 * A request that fails removes its key, so the client can retry. A key left "running" by a crashed
 * process is taken over after two minutes (longer than any emit).
 */
@ApplicationScoped
public class IdempotencyManager {

    /** A response stored for replay. */
    public record Stored(int status, JsonNode body) {}

    /** The outcome of {@link #begin}: a claim to go ahead with, or a stored response to replay. */
    public record Begun(UUID claim, Stored replay) {}

    private final IdempotencyProvider keys;
    private final TenantAccess access;
    private final ObjectMapper json;

    public IdempotencyManager(IdempotencyProvider keys, TenantAccess access, ObjectMapper json) {
        this.keys = keys;
        this.access = access;
        this.json = json;
    }

    /**
     * A {@code 201} create under an optional {@code Idempotency-Key} (company, series, member and
     * webhook creates; DESIGN.md §12). {@code work} runs in a transaction of its own and must open
     * the tenant itself ({@link TenantAccess#enter}); with a key, the caller is authorized at
     * {@code minimum} before the key is touched, and {@code stored} gives the body kept for replay
     * (a secret shown once is left out of it, never stored).
     */
    public <T> Response create(UUID tenantId, Role minimum, String key, String requestHash, Supplier<T> work, Function<T, Object> stored) {
        if (key == null) {
            return Response.status(201).entity(QuarkusTransaction.requiringNew().call(work::get)).build();
        }
        if (key.isBlank() || key.length() > 255) {
            throw ApiException.invalid("invalid_request", "La Idempotency-Key debe tener entre 1 y 255 caracteres.", null);
        }
        // Authorize before touching the idempotency table
        QuarkusTransaction.requiringNew().run(() -> access.enter(tenantId, minimum));
        Begun begun = begin(tenantId, key, requestHash);
        if (begun.replay() != null) {
            return Response.status(begun.replay().status()).entity(begun.replay().body()).build();
        }
        try {
            T created = QuarkusTransaction.requiringNew().call(() -> {
                T result = work.get();
                complete(tenantId, key, begun.claim(), 201, json.valueToTree(stored.apply(result)));
                return result;
            });
            return Response.status(201).entity(created).build();
        } catch (RuntimeException e) {
            abandon(tenantId, key, begun.claim());
            throw e;
        }
    }

    /** The request hash of a create: its kind, the ids of its path and its canonical body. */
    public String hash(String kind, Object path, Object body) {
        try {
            JsonNode canonical = EmitManager.canonical(json.valueToTree(body));
            return FileStore.sha256((kind + ":" + path + ":" + json.writeValueAsString(canonical)).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Claims the key, or returns the stored response. Throws 409/422 as described above. */
    public Begun begin(UUID tenantId, String key, String requestHash) {
        UUID claim = UUID.randomUUID();
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            if (keys.insertKey(tenantId, key, requestHash, claim)) {
                return new Begun(claim, null);
            }
            Optional<IdempotencyProvider.IdempotencyKey> rows = keys.getKeyForUpdate(tenantId, key);
            if (rows.isEmpty()) {
                // Removed by a failing first request in the meantime
                throw ApiException.conflict("idempotency_in_progress", "Una solicitud con la misma Idempotency-Key está en proceso.");
            }
            IdempotencyProvider.IdempotencyKey row = rows.get();
            if (!requestHash.equals(row.requestHash())) {
                throw new ApiException(422, "idempotency_key_reused",
                        "La Idempotency-Key ya se usó con una solicitud diferente.");
            }
            if (row.statusCode() == null) {
                if (row.stale()) {
                    // Abandoned by a crashed process: take it over with a new claim
                    keys.takeOverKey(tenantId, key, claim);
                    return new Begun(claim, null);
                }
                throw ApiException.conflict("idempotency_in_progress", "Una solicitud con la misma Idempotency-Key está en proceso.");
            }
            return new Begun(null, new Stored(row.statusCode(), json.readTree(row.response())));
        });
    }

    /**
     * Stores the response, in the business transaction (it commits with the document). Fails if the
     * claim was taken over meanwhile, so this request rolls back instead of creating a duplicate.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void complete(UUID tenantId, String key, UUID claim, int status, Object body) {
        int updated = keys.completeKey(tenantId, key, claim, status, write(body));
        if (updated != 1) {
            throw ApiException.conflict("idempotency_in_progress", "Una solicitud con la misma Idempotency-Key está en proceso.");
        }
    }

    /** The request failed: free the key (only its own claim) so the client can retry. */
    public void abandon(UUID tenantId, String key, UUID claim) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            keys.deleteKey(tenantId, key, claim);
        });
    }

    private String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
