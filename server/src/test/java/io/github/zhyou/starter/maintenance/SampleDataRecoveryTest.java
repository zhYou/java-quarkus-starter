package io.github.zhyou.starter.tests.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.maintenance.SampleDataTransfer;
import io.github.zhyou.starter.testing.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("recovery")
class SampleDataRecoveryTest {
  @TempDir Path dir;

  @Test
  void postgresDumpRestoreIntoNewTargetPreservesSource() throws Exception {
    try (var source = (PostgresEnvironment) PostgresEnvironment.start();
        var target = PostgresEnvironment.startEmpty()) {
      seed(source);
      Path backup = dir.resolve("postgres.dump");
      source.dump(backup);
      target.restore(backup);
      assertEquals(rows(source), rows(target));
      assertEquals(2, rows(source).size());
    }
  }

  @Test
  void citusSampleExportIntoNewTopologyPreservesSource() throws Exception {
    try (var source = CitusEnvironment.start()) {
      seed(source);
      var before = rows(source);
      Path backup = dir.resolve("citus.json");
      try (var c = connect(source)) {
        SampleDataTransfer.exportData(c, backup);
      }
      source.suspend();
      try (var target = CitusEnvironment.start();
          var c = connect(target)) {
        SampleDataTransfer.importData(c, backup);
        assertEquals(before, rows(target));
      }
      source.restart();
      assertEquals(before, rows(source));
    }
  }

  private static Connection connect(DatabaseEnvironment db) throws SQLException {
    return DriverManager.getConnection(db.jdbcUrl(), db.runtimeUsername(), db.runtimePassword());
  }

  private static void seed(DatabaseEnvironment db) throws SQLException {
    try (var c = connect(db);
        var st =
            c.prepareStatement(
                "insert into orders(customer_id,id,description,status,created_at) values (?,?,?,?,'2026-10-29T01:02:03.123456Z')")) {
      for (String status : List.of("NEW", "COMPLETED")) {
        st.setObject(1, new UUID(0, 1));
        st.setObject(2, UUID.randomUUID());
        st.setString(3, "你好 \"sample\"\nrecord");
        st.setString(4, status);
        st.executeUpdate();
      }
    }
  }

  private static List<String> rows(DatabaseEnvironment db) throws SQLException {
    var rows = new ArrayList<String>();
    try (var c = connect(db);
        var st = c.createStatement();
        var rs =
            st.executeQuery(
                "select customer_id,id,description,status,created_at from orders order by customer_id,id")) {
      while (rs.next())
        rows.add(
            rs.getString(1)
                + rs.getString(2)
                + rs.getString(3)
                + rs.getString(4)
                + rs.getTimestamp(5).toInstant());
    }
    return rows;
  }
}
