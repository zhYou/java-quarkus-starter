package io.github.zhyou.starter.api;

import io.github.zhyou.starter.domain.Order;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID customerId, UUID id, String description, String status, Instant createdAt) {
  public static OrderResponse from(Order o) {
    return new OrderResponse(
        o.customerId(), o.id(), o.description(), o.status().name(), o.createdAt());
  }
}
