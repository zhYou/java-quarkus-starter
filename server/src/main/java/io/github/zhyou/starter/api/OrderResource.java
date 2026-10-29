package io.github.zhyou.starter.api;

import io.github.zhyou.starter.application.OrderService;
import io.github.zhyou.starter.domain.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/api/v1/customers/{customerId}/orders")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class OrderResource {
  @Inject OrderService service;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(
      @PathParam("customerId") String customer,
      @Valid CreateOrderRequest request,
      @Context UriInfo uri) {
    var order = service.create(uuid(customer), request == null ? null : request.description());
    return Response.created(uri.getAbsolutePathBuilder().path(order.id().toString()).build())
        .entity(OrderResponse.from(order))
        .build();
  }

  @GET
  public List<OrderResponse> list(
      @PathParam("customerId") String customer,
      @QueryParam("limit") @DefaultValue("20") String limit) {
    int n;
    try {
      n = Integer.parseInt(limit);
    } catch (NumberFormatException e) {
      throw new OrderException(OrderErrorCode.INVALID_INPUT, "Limit must be 1 to 100");
    }
    return service.list(uuid(customer), n).stream().map(OrderResponse::from).toList();
  }

  @GET
  @Path("/{id}")
  public OrderResponse get(@PathParam("customerId") String customer, @PathParam("id") String id) {
    return OrderResponse.from(service.get(new OrderKey(uuid(customer), uuid(id))));
  }

  @POST
  @Path("/{id}/complete")
  public OrderResponse complete(
      @PathParam("customerId") String customer, @PathParam("id") String id) {
    return OrderResponse.from(service.complete(new OrderKey(uuid(customer), uuid(id))));
  }

  private static UUID uuid(String value) {
    try {
      UUID id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
      return id;
    } catch (IllegalArgumentException e) {
      throw new OrderException(OrderErrorCode.INVALID_INPUT, "A valid UUID is required");
    }
  }
}
