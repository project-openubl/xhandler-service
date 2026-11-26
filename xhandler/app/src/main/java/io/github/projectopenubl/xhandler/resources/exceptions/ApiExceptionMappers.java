package io.github.projectopenubl.xhandler.resources.exceptions;

import io.github.projectopenubl.xhandler.representations.idm.ErrorRepresentation;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Maps exceptions to {@link ErrorRepresentation} bodies. */
public class ApiExceptionMappers {

    @ServerExceptionMapper
    public Response api(ApiException e) {
        return Response.status(e.status()).entity(new ErrorRepresentation(e.kind(), e.getMessage(), e.detail())).build();
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return Response.status(422)
                .entity(new ErrorRepresentation("invalid_request", "La solicitud tiene datos inválidos.", detail))
                .build();
    }
}
