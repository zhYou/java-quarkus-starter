package io.github.zhyou.starter.testing;

import io.github.zhyou.starter.maintenance.*;
import java.sql.*;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresEnvironment implements DatabaseEnvironment {
  public static final String IMAGE =
      "postgres:18.6-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5";
  private final PostgreSQLContainer container;
  private final String runtimePassword = UUID.randomUUID().toString(),
      migrationPassword = UUID.randomUUID().toString();

  private PostgresEnvironment() {
    container =
        new PostgreSQLContainer(
                org.testcontainers.utility.DockerImageName.parse(IMAGE)
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("starter")
            .withUsername("bootstrap")
            .withPassword(UUID.randomUUID().toString());
  }

  public static PostgresEnvironment startEmpty() {
    var db = new PostgresEnvironment();
    try {
      db.container.start();
      db.roles();
      return db;
    } catch (RuntimeException e) {
      db.close();
      throw e;
    }
  }

  public static DatabaseEnvironment start() {
    var db = startEmpty();
    try {
      MigrationRunner.migrate(
          new MigrationConfig(
              DatabaseMode.SINGLE, db.jdbcUrl(), db.migrationUsername(), db.migrationPassword()));
      return db;
    } catch (RuntimeException e) {
      db.close();
      throw e;
    }
  }

  private void roles() {
    try (var c =
            DriverManager.getConnection(
                jdbcUrl(), container.getUsername(), container.getPassword());
        var st = c.createStatement()) {
      st.execute("CREATE ROLE app_runtime LOGIN PASSWORD '" + runtimePassword + "'");
      st.execute("CREATE ROLE app_migration LOGIN PASSWORD '" + migrationPassword + "'");
      st.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
      st.execute("ALTER SCHEMA public OWNER TO app_migration");
      st.execute("GRANT USAGE ON SCHEMA public TO app_runtime");
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot initialize test roles", e);
    }
  }

  public String jdbcUrl() {
    return container.getJdbcUrl();
  }

  public String runtimeUsername() {
    return "app_runtime";
  }

  public String runtimePassword() {
    return runtimePassword;
  }

  public String migrationUsername() {
    return "app_migration";
  }

  public String migrationPassword() {
    return migrationPassword;
  }

  public void dump(java.nio.file.Path target) throws Exception {
    var result =
        container.execInContainer(
            "pg_dump",
            "-U",
            "app_migration",
            "-d",
            "starter",
            "--no-owner",
            "--format=custom",
            "-f",
            "/tmp/starter.dump");
    if (result.getExitCode() != 0)
      throw new IllegalStateException("pg_dump failed: " + result.getStderr());
    container.copyFileFromContainer("/tmp/starter.dump", target.toString());
  }

  public void restore(java.nio.file.Path source) throws Exception {
    container.copyFileToContainer(
        org.testcontainers.utility.MountableFile.forHostPath(source), "/tmp/starter.dump");
    var result =
        container.execInContainer(
            "pg_restore",
            "-U",
            "app_migration",
            "-d",
            "starter",
            "--clean",
            "--if-exists",
            "--no-owner",
            "--exit-on-error",
            "/tmp/starter.dump");
    if (result.getExitCode() != 0)
      throw new IllegalStateException("pg_restore failed: " + result.getStderr());
  }

  public void close() {
    container.stop();
  }
}
