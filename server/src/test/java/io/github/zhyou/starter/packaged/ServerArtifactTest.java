package io.github.zhyou.starter.tests.packaged;

import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.testing.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("packaged")
class ServerArtifactTest {
  @TempDir Path dir;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

  @Test
  void cliToHttpOnBothDatabaseModesAndGracefulShutdown() throws Exception {
    for (String mode : List.of("single", "sharding")) {
      try (DatabaseEnvironment db =
          mode.equals("single") ? PostgresEnvironment.start() : CitusEnvironment.start()) {
        String customer = UUID.randomUUID().toString();
        var created =
            ArtifactSupport.cli(
                dir,
                ArtifactSupport.environment(db),
                "--output=json",
                "orders",
                "create",
                "--customer-id",
                customer,
                "--description",
                "packaged " + mode);
        assertEquals(0, created.code(), created.err());
        String id = created.json().get("id").asText();
        int port = ArtifactSupport.port(), management = ArtifactSupport.port();
        var env = new HashMap<>(ArtifactSupport.environment(db));
        env.put("QUARKUS_HTTP_PORT", String.valueOf(port));
        env.put("QUARKUS_MANAGEMENT_PORT", String.valueOf(management));
        Path log = dir.resolve("server-" + mode + ".log");
        var server =
            ArtifactSupport.builder(
                    List.of(
                        ArtifactSupport.JAVA,
                        "-jar",
                        ArtifactSupport.ROOT
                            .resolve("server/build/quarkus-app/quarkus-run.jar")
                            .toString()),
                    env)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
          waitStatus(management, "/q/health/ready", 200, server, log);
          String path = "/api/v1/customers/" + customer + "/orders/" + id;
          var fetched = get(port, path);
          assertEquals(200, fetched.statusCode(), fetched.body());
          assertEquals(id, ArtifactSupport.JSON.readTree(fetched.body()).get("id").asText());
          var completed =
              http.send(
                  HttpRequest.newBuilder(
                          URI.create("http://127.0.0.1:" + port + path + "/complete"))
                      .timeout(Duration.ofSeconds(5))
                      .POST(HttpRequest.BodyPublishers.noBody())
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          assertEquals(200, completed.statusCode(), completed.body());
          assertEquals(
              "COMPLETED", ArtifactSupport.JSON.readTree(completed.body()).get("status").asText());
          var cliGet =
              ArtifactSupport.cli(
                  dir,
                  ArtifactSupport.environment(db),
                  "--output=json",
                  "orders",
                  "get",
                  "--customer-id",
                  customer,
                  "--id",
                  id);
          assertEquals(0, cliGet.code(), cliGet.err());
          assertEquals("COMPLETED", cliGet.json().get("status").asText());
          if (mode.equals("single")) {
            stopDatabase(db);
            waitStatus(management, "/q/health/ready", 503, server, log);
            assertEquals(200, get(management, "/q/health/live").statusCode());
          }
          server.destroy();
          assertTrue(
              server.waitFor(15, TimeUnit.SECONDS), "SIGTERM must stop server within grace period");
        } finally {
          server.destroyForcibly();
        }
      }
    }
  }

  private static void stopDatabase(DatabaseEnvironment db) {
    db.close();
  }

  private HttpResponse<String> get(int port, String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private void waitStatus(int port, String path, int expected, Process process, Path log)
      throws Exception {
    long deadline = System.nanoTime() + 35_000_000_000L;
    while (System.nanoTime() < deadline) {
      assertTrue(
          process.isAlive(),
          () -> {
            try {
              return Files.readString(log);
            } catch (Exception e) {
              return "server exited";
            }
          });
      try {
        if (get(port, path).statusCode() == expected) return;
      } catch (java.io.IOException ignored) {
      }
      Thread.sleep(150);
    }
    fail("Expected HTTP " + expected + " at " + path + "; logs: " + Files.readString(log));
  }
}
