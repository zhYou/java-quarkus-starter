package io.github.zhyou.starter.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class OrderEntityId implements Serializable {
  private static final long serialVersionUID = 1L;
  public UUID customerId;
  public UUID id;

  public OrderEntityId() {}

  public OrderEntityId(UUID customerId, UUID id) {
    this.customerId = customerId;
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof OrderEntityId k
        && Objects.equals(customerId, k.customerId)
        && Objects.equals(id, k.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId, id);
  }
}
