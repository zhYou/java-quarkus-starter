package io.github.zhyou.starter.testing;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.application.*;
import io.github.zhyou.starter.domain.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

public abstract class OrderRepositoryContract {
  @Inject OrderRepository repository;

  @Test
  void compositeKeyAndStableBoundedList() {
    UUID customer = UUID.randomUUID(), other = UUID.randomUUID(), id = UUID.randomUUID();
    var first =
        new Order(customer, id, "first", OrderStatus.NEW, Instant.parse("2026-01-01T00:00:00Z"));
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              repository.insert(first);
              repository.insert(new Order(other, id, "other", OrderStatus.NEW, first.createdAt()));
              repository.insert(
                  new Order(
                      customer,
                      UUID.randomUUID(),
                      "later",
                      OrderStatus.NEW,
                      first.createdAt().plusSeconds(1)));
            });
    assertEquals(first, repository.findByKey(new OrderKey(customer, id)).orElseThrow());
    assertEquals(
        "other", repository.findByKey(new OrderKey(other, id)).orElseThrow().description());
    assertEquals(List.of(first), repository.listByCustomer(customer, 1));
    assertTrue(repository.findByKey(new OrderKey(UUID.randomUUID(), id)).isEmpty());
  }

  @Test
  void failedTransactionRollsBack() {
    var key = new OrderKey(UUID.randomUUID(), UUID.randomUUID());
    assertThrows(
        RuntimeException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(
                    () -> {
                      repository.insert(
                          new Order(
                              key.customerId(),
                              key.id(),
                              "rollback",
                              OrderStatus.NEW,
                              Instant.now()));
                      throw new IllegalStateException("abort");
                    }));
    assertTrue(repository.findByKey(key).isEmpty());
  }

  @Test
  void concurrentCompletionHasOneWinner() throws Exception {
    var key = new OrderKey(UUID.randomUUID(), UUID.randomUUID());
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                repository.insert(
                    new Order(key.customerId(), key.id(), "race", OrderStatus.NEW, Instant.now())));
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var gate = new CountDownLatch(1);
      var tasks = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 6; i++)
        tasks.add(
            pool.submit(
                () -> {
                  gate.await();
                  return QuarkusTransaction.requiringNew()
                      .call(() -> repository.completeIfNew(key));
                }));
      gate.countDown();
      int wins = 0;
      for (var task : tasks) if (task.get(20, TimeUnit.SECONDS)) wins++;
      assertEquals(1, wins);
    }
  }
}
