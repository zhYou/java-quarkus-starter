package io.github.zhyou.starter.api;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

@Provider
@Priority(1)
public class ValidationProblemMapper
    implements ExceptionMapper<ResteasyReactiveViolationException> {
  @Context ContainerRequestContext request;

  public Response toResponse(ResteasyReactiveViolationException e) {
    return Response.status(400)
        .type("application/problem+json")
        .entity(
            new ProblemResponse(
                "about:blank",
                "Bad Request",
                400,
                "Invalid request body",
                request.getUriInfo().getPath(),
                "INVALID_INPUT",
                String.valueOf(request.getProperty(RequestIdFilter.KEY))))
        .build();
  }
}
