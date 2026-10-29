package io.github.zhyou.starter.tests.sharding;

import io.github.zhyou.starter.testing.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;

@Tag("sharding")
@QuarkusTest
@QuarkusTestResource(value = CitusResource.class, restrictToAnnotatedClass = true)
public class CitusOrderRepositoryTest extends OrderRepositoryContract {
  @jakarta.inject.Inject io.agroal.api.AgroalDataSource dataSource;

  @org.junit.jupiter.api.Test
  void connectedToDistributedOrders() throws Exception {
    try (var c = dataSource.getConnection();
        var st = c.createStatement();
        var rows =
            st.executeQuery(
                "select count(*) from pg_dist_partition where logicalrelid='orders'::regclass")) {
      rows.next();
      org.junit.jupiter.api.Assertions.assertEquals(1, rows.getInt(1));
    }
  }
}
