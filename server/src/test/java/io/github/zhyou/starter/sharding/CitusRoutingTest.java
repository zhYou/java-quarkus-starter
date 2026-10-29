package io.github.zhyou.starter.tests.sharding;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.maintenance.*;
import io.github.zhyou.starter.testing.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("sharding")
class CitusRoutingTest {
  @Test
  void physicalRoutingAndRestartPreserveRows() throws Exception {
    try (var db = CitusEnvironment.start()) {
      var keys = new HashSet<String>();
      try (var c =
              DriverManager.getConnection(
                  db.jdbcUrl(), db.runtimeUsername(), db.runtimePassword());
          var st =
              c.prepareStatement(
                  "insert into orders(customer_id,id,description,created_at,status) values (?,?,?,now(),'NEW')")) {
        for (int customer = 1; customer <= 32; customer++)
          for (int n = 1; n <= 4; n++) {
            UUID cid = new UUID(0, customer), id = new UUID(0, n);
            st.setObject(1, cid);
            st.setObject(2, id);
            st.setString(3, "route");
            st.executeUpdate();
            keys.add(cid + "/" + id);
          }
      }
      List<Long> shards = new ArrayList<>();
      try (var c =
              DriverManager.getConnection(
                  db.jdbcUrl(), db.migrationUsername(), db.migrationPassword());
          var st = c.createStatement();
          var rs =
              st.executeQuery(
                  "select shardid from pg_dist_shard where logicalrelid='orders'::regclass")) {
        while (rs.next()) shards.add(rs.getLong(1));
      }
      assertEquals(8, shards.size());
      var observed = new HashSet<String>();
      var customerNode = new HashMap<String, Integer>();
      int node = 0;
      for (String url : db.workerJdbcUrls()) {
        int count = 0;
        try (var c =
            DriverManager.getConnection(url, db.migrationUsername(), db.migrationPassword())) {
          for (long shard : shards) {
            String table = "public.orders_" + shard;
            boolean exists;
            try (var st = c.prepareStatement("select to_regclass(?)")) {
              st.setString(1, table);
              try (var rs = st.executeQuery()) {
                rs.next();
                exists = rs.getObject(1) != null;
              }
            }
            if (!exists) continue;
            try (var st = c.createStatement();
                var rs = st.executeQuery("select customer_id,id from only " + table)) {
              while (rs.next()) {
                String cid = rs.getString(1);
                assertTrue(observed.add(cid + "/" + rs.getString(2)), "duplicate physical record");
                assertEquals(node, customerNode.computeIfAbsent(cid, k -> nodeIndex(url, db)));
                count++;
              }
            }
          }
        }
        assertTrue(count > 0, "each worker must store real records");
        node++;
      }
      assertEquals(keys, observed);
      db.restart();
      MigrationRunner.migrate(
          new MigrationConfig(
              DatabaseMode.SHARDING, db.jdbcUrl(), db.migrationUsername(), db.migrationPassword()));
      MigrationRunner.migrate(
          new MigrationConfig(
              DatabaseMode.SHARDING, db.jdbcUrl(), db.migrationUsername(), db.migrationPassword()));
      try (var c =
              DriverManager.getConnection(
                  db.jdbcUrl(), db.runtimeUsername(), db.runtimePassword());
          var st = c.createStatement();
          var rs = st.executeQuery("select count(*) from orders")) {
        assertTrue(rs.next());
        assertEquals(128, rs.getInt(1));
      }
    }
  }

  private int nodeIndex(String url, CitusEnvironment db) {
    return db.workerJdbcUrls().indexOf(url);
  }
}
