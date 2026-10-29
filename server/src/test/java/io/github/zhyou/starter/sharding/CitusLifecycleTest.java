package io.github.zhyou.starter.tests.sharding;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.testing.CitusEnvironment;
import org.junit.jupiter.api.*;

@Tag("sharding")
class CitusLifecycleTest {
  @Test
  void repeatedTopologyDoesNotStartNewMetadataTransactions() throws Exception {
    try (var db = CitusEnvironment.start()) {
      db.recoverTransactions();
      assertEquals(0, db.pendingTransactions());
      db.applyTopology();
      assertEquals(
          0, db.pendingTransactions(), "Already-correct topology must not run metadata mutations");
    }
  }
}
