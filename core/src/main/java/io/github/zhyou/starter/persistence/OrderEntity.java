package io.github.zhyou.starter.persistence;

import io.github.zhyou.starter.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@IdClass(OrderEntityId.class)
public class OrderEntity {
  @Id
  @Column(name = "customer_id", nullable = false)
  public UUID customerId;

  @Id
  @Column(nullable = false)
  public UUID id;

  @Column(nullable = false, length = 200)
  public String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public OrderStatus status;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  public OrderEntity() {}

  public static OrderEntity from(Order o) {
    var e = new OrderEntity();
    e.customerId = o.customerId();
    e.id = o.id();
    e.description = o.description();
    e.status = o.status();
    e.createdAt = o.createdAt();
    return e;
  }

  public Order toOrder() {
    return new Order(customerId, id, description, status, createdAt);
  }
}
