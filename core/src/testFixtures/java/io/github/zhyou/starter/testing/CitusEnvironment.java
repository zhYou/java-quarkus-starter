package io.github.zhyou.starter.testing;

import io.github.zhyou.starter.maintenance.*;
import java.sql.*;
import java.util.*;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class CitusEnvironment implements DatabaseEnvironment {
  public static final String IMAGE =
      "citusdata/citus:14.2.0-alpine@sha256:703d5b9f830dc38e265c0135223f0e12a5dc82cb2b0899b47ae37290cdacfce0";
  private final Network network = Network.newNetwork();
  private final String adminPassword = UUID.randomUUID().toString(),
      runtimePassword = UUID.randomUUID().toString(),
      migrationPassword = UUID.randomUUID().toString();
  private final List<PostgreSQLContainer> nodes = new ArrayList<>();

  private CitusEnvironment() {
    for (String alias : List.of("coordinator", "worker1", "worker2")) {
      nodes.add(
          new PostgreSQLContainer(
                  DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
              .withDatabaseName("starter")
              .withUsername("bootstrap")
              .withPassword(adminPassword)
              .withNetwork(network)
              .withNetworkAliases(alias)
              .withCommand(
                  "postgres",
                  "-c",
                  "shared_preload_libraries=citus",
                  "-c",
                  "citus.node_conninfo=sslmode=disable"));
    }
  }

  public static CitusEnvironment start() {
    var db = new CitusEnvironment();
    try {
      for (var node : db.nodes) {
        node.start();
        db.initialize(node);
      }
      db.applyTopology();
      MigrationRunner.migrate(
          new MigrationConfig(
              DatabaseMode.SHARDING, db.jdbcUrl(), db.migrationUsername(), db.migrationPassword()));
      return db;
    } catch (Exception e) {
      db.close();
      throw new IllegalStateException("Cannot initialize Citus test environment", e);
    }
  }

  private String currentUrl(PostgreSQLContainer node) {
    var binding =
        node.getDockerClient()
            .inspectContainerCmd(node.getContainerId())
            .exec()
            .getNetworkSettings()
            .getPorts()
            .getBindings()
            .get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0];
    return "jdbc:postgresql://"
        + node.getHost()
        + ":"
        + binding.getHostPortSpec()
        + "/starter?connectTimeout=2&socketTimeout=5";
  }

  private Connection admin(PostgreSQLContainer node) throws SQLException {
    return DriverManager.getConnection(currentUrl(node), "bootstrap", adminPassword);
  }

  private void initialize(PostgreSQLContainer node) throws SQLException {
    try (var c = admin(node);
        var st = c.createStatement()) {
      st.execute("CREATE EXTENSION IF NOT EXISTS citus");
      st.execute("CREATE ROLE app_runtime LOGIN PASSWORD '" + runtimePassword + "'");
      st.execute("CREATE ROLE app_migration LOGIN PASSWORD '" + migrationPassword + "'");
      st.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
      st.execute("ALTER SCHEMA public OWNER TO app_migration");
      st.execute("GRANT USAGE ON SCHEMA public TO app_runtime");
      try (var auth =
          c.prepareStatement(
              "insert into pg_dist_authinfo(nodeid,rolename,authinfo) values (0,?,?)")) {
        for (var entry :
            Map.of(
                    "bootstrap",
                    adminPassword,
                    "app_runtime",
                    runtimePassword,
                    "app_migration",
                    migrationPassword)
                .entrySet()) {
          auth.setString(1, entry.getKey());
          auth.setString(2, "password=" + entry.getValue());
          auth.executeUpdate();
        }
      }
    }
  }

  public String jdbcUrl() {
    return currentUrl(nodes.getFirst());
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

  public List<String> workerJdbcUrls() {
    return nodes.subList(1, 3).stream().map(this::currentUrl).toList();
  }

  public void applyTopology() throws Exception {
    String sql;
    try (var stream = CitusEnvironment.class.getResourceAsStream("/citus-topology.sql")) {
      if (stream == null) throw new IllegalStateException("Missing topology test resource");
      sql =
          new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
              .replaceAll("(?m)^\\\\set.*$", "");
    }
    try (var c = admin(nodes.getFirst());
        var st = c.createStatement()) {
      st.execute(sql);
    }
  }

  public void recoverTransactions() throws SQLException {
    try (var c = admin(nodes.getFirst());
        var st = c.createStatement()) {
      st.execute("select recover_prepared_transactions()");
    }
  }

  public long pendingTransactions() throws SQLException {
    try (var c = admin(nodes.getFirst());
        var st = c.createStatement();
        var rs = st.executeQuery("select count(*) from pg_dist_transaction")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  public void suspend() {
    for (var node : nodes) node.getDockerClient().stopContainerCmd(node.getContainerId()).exec();
  }

  public void restart() {
    for (var node : List.of(nodes.get(1), nodes.get(2), nodes.getFirst())) {
      node.getDockerClient().restartContainerCmd(node.getContainerId()).exec();
      long deadline = System.nanoTime() + 30_000_000_000L;
      boolean ready = false;
      SQLException lastFailure = null;
      while (System.nanoTime() < deadline) {
        try (var c = admin(node)) {
          if (c.isValid(1)) {
            ready = true;
            break;
          }
        } catch (SQLException failure) {
          lastFailure = failure;
        }
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Restart interrupted");
        }
      }
      if (!ready)
        throw new IllegalStateException(
            "Citus restart timed out: "
                + node.getLogs().substring(Math.max(0, node.getLogs().length() - 1800)),
            lastFailure);
    }
  }

  public void close() {
    for (var node : nodes.reversed()) {
      try {
        node.stop();
      } catch (RuntimeException ignored) {
      }
    }
    network.close();
  }
}
