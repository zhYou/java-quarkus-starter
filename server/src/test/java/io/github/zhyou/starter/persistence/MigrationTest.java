package io.github.zhyou.starter.tests.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.maintenance.*;
import io.github.zhyou.starter.testing.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigrationTest {
  @Test
  void emptyRepeatAndRuntimePrivileges() throws Exception {
    try (var db = PostgresEnvironment.start()) {
      MigrationRunner.migrate(
          new MigrationConfig(
              DatabaseMode.SINGLE, db.jdbcUrl(), db.migrationUsername(), db.migrationPassword()));
      try (var c =
              DriverManager.getConnection(
                  db.jdbcUrl(), db.runtimeUsername(), db.runtimePassword());
          var st = c.createStatement()) {
        st.executeUpdate(
            "insert into orders(customer_id,id,description,created_at,status) values ('"
                + UUID.randomUUID()
                + "','"
                + UUID.randomUUID()
                + "','runtime',now(),'NEW')");
        assertThrows(SQLException.class, () -> st.execute("create table forbidden(id int)"));
      }
    }
  }

  @Test
  void upgradesExistingV1Rows() throws Exception {
    try (var db = PostgresEnvironment.startEmpty()) {
      Flyway.configure()
          .dataSource(db.jdbcUrl(), db.migrationUsername(), db.migrationPassword())
          .locations("classpath:db/migration/common")
          .target("1")
          .load()
          .migrate();
      try (var c =
              DriverManager.getConnection(
                  db.jdbcUrl(), db.migrationUsername(), db.migrationPassword());
          var st = c.createStatement()) {
        st.executeUpdate(
            "insert into orders(customer_id,id,description,created_at) values ('"
                + UUID.randomUUID()
                + "','"
                + UUID.randomUUID()
                + "','old',now())");
      }
      MigrationRunner.migrate(
          new MigrationConfig(
              DatabaseMode.SINGLE, db.jdbcUrl(), db.migrationUsername(), db.migrationPassword()));
      try (var c =
              DriverManager.getConnection(
                  db.jdbcUrl(), db.runtimeUsername(), db.runtimePassword());
          var st = c.createStatement();
          var rs = st.executeQuery("select status from orders")) {
        assertTrue(rs.next());
        assertEquals("NEW", rs.getString(1));
      }
    }
  }
}
