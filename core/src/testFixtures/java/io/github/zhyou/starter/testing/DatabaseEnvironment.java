package io.github.zhyou.starter.testing;

import java.util.Map;

public interface DatabaseEnvironment extends AutoCloseable {
  String jdbcUrl();

  String runtimeUsername();

  String runtimePassword();

  String migrationUsername();

  String migrationPassword();

  default Map<String, String> applicationConfig() {
    return Map.of(
        "quarkus.datasource.jdbc.url",
        jdbcUrl(),
        "quarkus.datasource.username",
        runtimeUsername(),
        "quarkus.datasource.password",
        runtimePassword(),
        "quarkus.datasource.devservices.enabled",
        "false");
  }

  @Override
  void close();
}
