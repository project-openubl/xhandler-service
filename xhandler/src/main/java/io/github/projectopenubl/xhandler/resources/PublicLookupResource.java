package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.representations.idm.LookupResultRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.LookupRequestRepresentation;
import io.github.projectopenubl.xhandler.utils.PublicStatus;
import io.github.projectopenubl.xhandler.models.PublicLookupProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * Lookup by printed data (DESIGN.md §12): answers only the status, never the files. Production
 * documents only. RUC + serie-número + date + total is low-entropy: bot protection and rate limits
 * belong to the gateway in front of this endpoint.
 */
@Path("/api/v1/public/lookup")
public class PublicLookupResource {

    private final PublicLookupProvider lookups;

    public PublicLookupResource(PublicLookupProvider lookups) {
        this.lookups = lookups;
    }

    @Operation(operationId = "lookupDocument")
    @POST
    public LookupResultRepresentation lookup(@Valid LookupRequestRepresentation request) {
        return QuarkusTransaction.requiringNew().call(() -> {
            String status = lookups.getPublicDocumentStatus(request.ruc(), request.tipoComprobante(), request.serie(), request.numero(),
                    request.fechaEmision(), request.total());
            return new LookupResultRepresentation(status == null ? "no_encontrado" : PublicStatus.of(status));
        });
    }
}
