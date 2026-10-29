package io.github.zhyou.starter.tests.packaged;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("packaged")
class CliArtifactTest {
  @TempDir Path dir;

  @Test
  void offlineHelpVersionAndErrors() throws Exception {
    for (String[] args :
        new String[][] {
          {"--help"}, {"--version"}, {"orders", "--help"}, {"orders", "create", "--help"}
        }) {
      var result = ArtifactSupport.cli(dir, Map.of(), args);
      assertEquals(0, result.code(), result.err());
      assertFalse(result.out().isBlank());
    }
    var invalid =
        ArtifactSupport.cli(
            dir,
            Map.of(),
            "--output=json",
            "orders",
            "get",
            "--customer-id",
            "invalid",
            "--id",
            "bad");
    assertEquals(2, invalid.code());
    assertEquals("INVALID_INPUT", invalid.json().get("code").asText());
  }

  @Test
  void startupFailureIsCleanJsonAndRedactsCredentials() throws Exception {
    String secret = "never-log-this-test-password";
    var result =
        ArtifactSupport.cli(
            dir,
            Map.of(
                "APP_DB_JDBC_URL",
                "jdbc:postgresql://127.0.0.1:1/unavailable?connectTimeout=1&socketTimeout=1",
                "APP_DB_USERNAME",
                "app_runtime",
                "APP_DB_PASSWORD",
                secret),
            "--output=json",
            "orders",
            "list",
            "--customer-id",
            UUID.randomUUID().toString());
    assertEquals(1, result.code(), result.err());
    assertEquals("INTERNAL_ERROR", result.json().get("code").asText());
    assertFalse(result.out().contains(secret));
    assertFalse(result.err().contains(secret));
    assertFalse(result.err().contains("at io."), result.err());
  }
}
