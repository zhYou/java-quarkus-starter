package io.github.zhyou.starter.application;

import static io.github.zhyou.starter.domain.OrderErrorCode.*;
import static io.github.zhyou.starter.domain.OrderStatus.*;

import io.github.zhyou.starter.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderService {
  private final OrderRepository repository;
  private final Clock clock;

  @Inject
  public OrderService(OrderRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public Order create(UUID customerId, String description) {
    requireCustomer(customerId);
    if (description == null
        || description.strip().isEmpty()
        || description.strip().length() > 200) {
      throw new OrderException(INVALID_INPUT, "Description must contain 1 to 200 characters");
    }
    Order order =
        new Order(customerId, UUID.randomUUID(), description.strip(), NEW, clock.instant());
    repository.insert(order);
    return order;
  }

  public Order get(OrderKey key) {
    if (key == null) throw new OrderException(INVALID_INPUT, "Order key is required");
    return repository
        .findByKey(key)
        .orElseThrow(() -> new OrderException(ORDER_NOT_FOUND, "Order not found"));
  }

  public List<Order> list(UUID customerId, int limit) {
    requireCustomer(customerId);
    if (limit < 1 || limit > 100) throw new OrderException(INVALID_INPUT, "Limit must be 1 to 100");
    return repository.listByCustomer(customerId, limit);
  }

  @Transactional
  public Order complete(OrderKey key) {
    Order order = get(key);
    if (order.status() != NEW || !repository.completeIfNew(key)) {
      throw new OrderException(ORDER_CONFLICT, "Order is already completed");
    }
    return new Order(
        order.customerId(), order.id(), order.description(), COMPLETED, order.createdAt());
  }

  private static void requireCustomer(UUID id) {
    if (id == null) throw new OrderException(INVALID_INPUT, "Customer ID is required");
  }
}
