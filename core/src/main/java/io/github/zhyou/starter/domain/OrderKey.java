package io.github.zhyou.starter.domain;

import java.util.UUID;

public record OrderKey(UUID customerId, UUID id) {
  public OrderKey {
    if (customerId == null || id == null) {
      throw new OrderException(OrderErrorCode.INVALID_INPUT, "Customer and order IDs are required");
    }
  }
}
