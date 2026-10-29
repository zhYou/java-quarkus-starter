package io.github.zhyou.starter.api;

import io.github.zhyou.starter.domain.*;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import org.jboss.logging.Logger;

@Provider
public class OrderExceptionMapper implements ExceptionMapper<Throwable> {
  @Context ContainerRequestContext request;

  public Response toResponse(Throwable e) {
    int status;
    String code, detail, title;
    if (e instanceof OrderException o) {
      status =
          switch (o.code()) {
            case INVALID_INPUT -> 400;
            case ORDER_NOT_FOUND -> 404;
            case ORDER_CONFLICT -> 409;
            case INTERNAL_ERROR -> 500;
          };
      code = o.code().name();
      detail = status == 500 ? "Order operation failed" : o.getMessage();
    } else if (e instanceof ConstraintViolationException
        || e instanceof BadRequestException
        || e instanceof com.fasterxml.jackson.core.JsonProcessingException) {
      status = 400;
      code = "INVALID_INPUT";
      detail = "Invalid request; check path, query and JSON body";
    } else if (e instanceof WebApplicationException w) {
      status = w.getResponse().getStatus();
      code = status == 404 ? "ORDER_NOT_FOUND" : "INVALID_INPUT";
      detail = "Request could not be processed";
    } else {
      status = 500;
      code = "INTERNAL_ERROR";
      detail = "Order operation failed";
      Logger.getLogger(OrderExceptionMapper.class)
          .errorf(
              "Request failed (%s), requestId=%s",
              e.getClass().getSimpleName(), request.getProperty(RequestIdFilter.KEY));
    }
    var knownStatus = Response.Status.fromStatusCode(status);
    title = knownStatus == null ? "Request Failed" : knownStatus.getReasonPhrase();
    var response = Response.status(status);
    if (e instanceof WebApplicationException web) {
      for (String header :
          java.util.List.of(
              "Allow",
              "WWW-Authenticate",
              "Proxy-Authenticate",
              "Retry-After",
              "Accept-Encoding")) {
        var values = web.getResponse().getHeaders().get(header);
        if (values != null) for (Object value : values) response.header(header, value);
      }
    }
    if (status == 405
        && e instanceof WebApplicationException web
        && !web.getResponse().getHeaders().containsKey("Allow")) {
      response.allow(allowedMethods());
    }
    return response
        .type("application/problem+json")
        .entity(
            new ProblemResponse(
                "about:blank",
                title,
                status,
                detail,
                request.getUriInfo().getPath(),
                code,
                String.valueOf(request.getProperty(RequestIdFilter.KEY))))
        .build();
  }

  private static java.util.Set<String> allowedMethods() {
    // Quarkus REST currently creates routing 405 exceptions without Allow.
    // Reuse its registered route metadata so new resources need no parallel list.
    var context = org.jboss.resteasy.reactive.server.core.CurrentRequestManager.get();
    var methods = new java.util.TreeSet<String>();
    if (context == null) return methods;
    var routes =
        new org.jboss.resteasy.reactive.server.mapping.RequestMapper<>(
            context.getDeployment().getClassMappers());
    String path = context.getPathWithoutPrefix();
    var match = routes.map(path);
    while (match != null) {
      String remaining = match.remaining.isEmpty() ? "/" : match.remaining;
      for (var handler : match.value.handlers) {
        if (handler
            instanceof org.jboss.resteasy.reactive.server.handlers.ClassRoutingHandler routing) {
          for (var entry : routing.getMappers().entrySet()) {
            if (entry.getKey() != null && entry.getValue().map(remaining) != null)
              methods.add(entry.getKey());
          }
        }
      }
      match = routes.continueMatching(path, match);
    }
    if (methods.contains("GET")) methods.add("HEAD");
    if (!methods.isEmpty()) methods.add("OPTIONS");
    return methods;
  }
}
