package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.SupportRequestProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.security.Actor;
import io.github.projectopenubl.xhandler.security.CurrentActor;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Support access, operator side: a platform operator asks a tenant for a session (DESIGN.md §26.1). */
@Path("/api/v1/support/sessions")
public class SupportRequestResource {

    public record RequestSession(@NotNull UUID tenantId, @NotBlank @Size(max = 500) String reason) {}

    public record SessionRequested(UUID id) {}

    private final SupportRequestProvider supportRequests;
    private final CurrentActor currentActor;

    public SupportRequestResource(SupportRequestProvider supportRequests, CurrentActor currentActor) {
        this.supportRequests = supportRequests;
        this.currentActor = currentActor;
    }

    @APIResponse(responseCode = "201", description = "Sesión de soporte solicitada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SessionRequested.class)))
    @Operation(operationId = "requestSupportSession")
    @POST
    @Transactional
    public Response request(@Valid RequestSession request) {
        Actor actor = currentActor.get();
        if (!actor.platformSupport()) {
            throw ApiException.forbidden();
        }
        UUID id = supportRequests.requestSupportSession(request.tenantId(), actor.subject(), request.reason())
                .orElseThrow(ApiException::notFound);
        return Response.status(201).entity(new SessionRequested(id)).build();
    }
}
