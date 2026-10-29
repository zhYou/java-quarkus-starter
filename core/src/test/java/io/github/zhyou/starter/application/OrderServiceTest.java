package io.github.zhyou.starter.application;

import static io.github.zhyou.starter.domain.OrderErrorCode.*;
import static io.github.zhyou.starter.domain.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
  private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
  private final MemoryRepository repository = new MemoryRepository();
  private final OrderService service =
      new OrderService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void createsNormalizedNewOrderWithClockTime() {
    Order order = service.create(CUSTOMER, " example ");
    assertEquals("example", order.description());
    assertEquals(NEW, order.status());
    assertEquals(CUSTOMER, order.customerId());
    assertEquals(NOW, order.createdAt());
    assertEquals(order, service.get(new OrderKey(CUSTOMER, order.id())));
  }

  @Test
  void rejectsInvalidInputBeforePersistence() {
    for (String description : List.of("", "   ", "x".repeat(201))) {
      assertEquals(
          INVALID_INPUT,
          assertThrows(OrderException.class, () -> service.create(CUSTOMER, description)).code());
    }
    assertEquals(
        INVALID_INPUT,
        assertThrows(OrderException.class, () -> service.create(null, "example")).code());
    assertEquals(
        INVALID_INPUT,
        assertThrows(OrderException.class, () -> service.list(CUSTOMER, 101)).code());
    assertEquals(
        INVALID_INPUT, assertThrows(OrderException.class, () -> service.list(CUSTOMER, 0)).code());
  }

  @Test
  void doesNotReturnOrderForAnotherCustomer() {
    Order order = service.create(CUSTOMER, "example");
    OrderKey wrongKey = new OrderKey(UUID.randomUUID(), order.id());
    assertEquals(
        ORDER_NOT_FOUND, assertThrows(OrderException.class, () -> service.get(wrongKey)).code());
  }

  @Test
  void completesOnlyOnceAndReturnsUpdatedState() {
    Order order = service.create(CUSTOMER, "example");
    OrderKey key = new OrderKey(CUSTOMER, order.id());
    assertEquals(COMPLETED, service.complete(key).status());
    assertEquals(
        ORDER_CONFLICT, assertThrows(OrderException.class, () -> service.complete(key)).code());
  }

  @Test
  void reportsConditionalUpdateRaceAsConflict() {
    Order order = service.create(CUSTOMER, "example");
    repository.loseRace = true;
    assertEquals(
        ORDER_CONFLICT,
        assertThrows(
                OrderException.class, () -> service.complete(new OrderKey(CUSTOMER, order.id())))
            .code());
  }

  private static class MemoryRepository implements OrderRepository {
    private final Map<OrderKey, Order> orders = new HashMap<>();
    private boolean loseRace;

    public void insert(Order order) {
      orders.put(new OrderKey(order.customerId(), order.id()), order);
    }

    public Optional<Order> findByKey(OrderKey key) {
      return Optional.ofNullable(orders.get(key));
    }

    public List<Order> listByCustomer(UUID id, int limit) {
      return orders.values().stream()
          .filter(o -> o.customerId().equals(id))
          .sorted(Comparator.comparing(Order::createdAt).thenComparing(Order::id))
          .limit(limit)
          .toList();
    }

    public boolean completeIfNew(OrderKey key) {
      if (loseRace) return false;
      Order previous = orders.get(key);
      if (previous == null || previous.status() != NEW) return false;
      orders.put(
          key,
          new Order(
              previous.customerId(),
              previous.id(),
              previous.description(),
              COMPLETED,
              previous.createdAt()));
      return true;
    }
  }
}
