package io.github.zhyou.starter.api;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

@Provider
public class JsonProblemMapper implements ExceptionMapper<MismatchedInputException> {
  @Context ContainerRequestContext request;

  public Response toResponse(MismatchedInputException e) {
    return Response.status(400)
        .type("application/problem+json")
        .entity(
            new ProblemResponse(
                "about:blank",
                "Bad Request",
                400,
                "Invalid JSON body",
                request.getUriInfo().getPath(),
                "INVALID_INPUT",
                String.valueOf(request.getProperty(RequestIdFilter.KEY))))
        .build();
  }
}
