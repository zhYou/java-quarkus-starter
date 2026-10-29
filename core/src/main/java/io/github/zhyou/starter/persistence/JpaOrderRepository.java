package io.github.zhyou.starter.persistence;

import io.github.zhyou.starter.application.OrderRepository;
import io.github.zhyou.starter.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;

@ApplicationScoped
public class JpaOrderRepository implements OrderRepository {
  @Inject EntityManager em;

  public void insert(Order o) {
    em.persist(OrderEntity.from(o));
  }

  public Optional<Order> findByKey(OrderKey key) {
    return Optional.ofNullable(
            em.find(OrderEntity.class, new OrderEntityId(key.customerId(), key.id())))
        .map(OrderEntity::toOrder);
  }

  public List<Order> listByCustomer(UUID customerId, int limit) {
    return em.createQuery(
            "from OrderEntity where customerId=:customer order by createdAt,id", OrderEntity.class)
        .setParameter("customer", customerId)
        .setMaxResults(limit)
        .getResultStream()
        .map(OrderEntity::toOrder)
        .toList();
  }

  public boolean completeIfNew(OrderKey key) {
    return em.createQuery(
                "update OrderEntity set status=:next where customerId=:customer and id=:id and status=:current")
            .setParameter("next", OrderStatus.COMPLETED)
            .setParameter("customer", key.customerId())
            .setParameter("id", key.id())
            .setParameter("current", OrderStatus.NEW)
            .executeUpdate()
        == 1;
  }
}
