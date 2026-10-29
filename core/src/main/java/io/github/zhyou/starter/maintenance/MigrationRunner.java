package io.github.zhyou.starter.maintenance;

import org.flywaydb.core.Flyway;

public final class MigrationRunner {
  private MigrationRunner() {}

  public static void migrate(MigrationConfig config) {
    var locations =
        config.mode() == DatabaseMode.SHARDING
            ? new String[] {"classpath:db/migration/common", "classpath:db/migration/citus"}
            : new String[] {"classpath:db/migration/common"};
    Flyway.configure()
        .dataSource(config.jdbcUrl(), config.username(), config.password())
        .locations(locations)
        .cleanDisabled(true)
        .load()
        .migrate();
  }

  public static void main(String[] args) {
    try {
      migrate(MigrationConfig.fromEnvironment(System.getenv()));
    } catch (Exception e) {
      System.err.println(
          "Database migration failed; check connectivity, credentials and schema compatibility");
      System.exit(1);
    }
  }
}
