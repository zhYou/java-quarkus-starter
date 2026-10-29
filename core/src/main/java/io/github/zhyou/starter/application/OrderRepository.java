package io.github.zhyou.starter.application;

import io.github.zhyou.starter.domain.Order;
import io.github.zhyou.starter.domain.OrderKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
  void insert(Order order);

  Optional<Order> findByKey(OrderKey key);

  List<Order> listByCustomer(UUID customerId, int limit);

  boolean completeIfNew(OrderKey key);
}
