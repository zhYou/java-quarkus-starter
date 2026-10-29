package io.github.zhyou.starter.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.jboss.logging.MDC;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
  public static final String KEY = "requestId";

  public void filter(ContainerRequestContext request) {
    String id = UUID.randomUUID().toString();
    request.setProperty(KEY, id);
    MDC.put(KEY, id);
  }

  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    response.getHeaders().putSingle("X-Request-ID", request.getProperty(KEY));
    MDC.remove(KEY);
  }
}
