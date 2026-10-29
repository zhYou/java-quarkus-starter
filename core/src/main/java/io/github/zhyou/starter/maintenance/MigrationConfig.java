package io.github.zhyou.starter.maintenance;

import java.util.Map;

public record MigrationConfig(DatabaseMode mode, String jdbcUrl, String username, String password) {
  public MigrationConfig {
    if (mode == null
        || jdbcUrl == null
        || !jdbcUrl.startsWith("jdbc:postgresql:")
        || username == null
        || username.isBlank()
        || password == null
        || password.isBlank())
      throw new IllegalArgumentException("Database migration configuration is incomplete");
  }

  public static MigrationConfig fromEnvironment(Map<String, String> env) {
    return new MigrationConfig(
        DatabaseMode.parse(env.getOrDefault("APP_DB_MODE", "single")),
        env.get("APP_DB_JDBC_URL"),
        env.get("APP_DB_MIGRATION_USERNAME"),
        env.get("APP_DB_MIGRATION_PASSWORD"));
  }

  @Override
  public String toString() {
    return "MigrationConfig[mode=" + mode + ", credentials=REDACTED]";
  }
}
