package io.github.projectopenubl.xhandler.models;

import java.util.UUID;

/** The signature log (DESIGN.md §6); joins the caller's transaction, which entered the tenant. */
public interface SignatureLogProvider {

    /** Logs one signature; {@code signingKeyId} or {@code platformKeyId} names the key used. */
    void addSignature(UUID id, UUID tenantId, UUID documentId, String serieNumero, UUID signingKeyId, UUID platformKeyId, String digestValue,
            String actor);
}
