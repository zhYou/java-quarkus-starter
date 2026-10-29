package io.github.zhyou.starter.tests.packaged;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import io.github.zhyou.starter.testing.DatabaseEnvironment;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class ArtifactSupport {
  static final ObjectMapper JSON = new ObjectMapper();
  static final Path ROOT =
      Path.of(System.getProperty("starter.projectRoot", "..")).toAbsolutePath().normalize();
  static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();

  record Result(int code, String out, String err) {
    JsonNode json() throws Exception {
      try (var parser = JSON.createParser(out)) {
        JsonNode node = JSON.readTree(parser);
        assertNotNull(node, out);
        assertNull(parser.nextToken(), out);
        return node;
      }
    }
  }

  static Map<String, String> environment(DatabaseEnvironment db) {
    return Map.of(
        "APP_DB_JDBC_URL",
        db.jdbcUrl(),
        "APP_DB_USERNAME",
        db.runtimeUsername(),
        "APP_DB_PASSWORD",
        db.runtimePassword());
  }

  static ProcessBuilder builder(List<String> command, Map<String, String> env) {
    var builder = new ProcessBuilder(command).directory(ROOT.toFile());
    builder
        .environment()
        .keySet()
        .removeIf(key -> key.startsWith("APP_DB_") || key.startsWith("QUARKUS_"));
    builder.environment().putAll(env);
    return builder;
  }

  static Result cli(Path dir, Map<String, String> env, String... args) throws Exception {
    var command =
        new ArrayList<String>(
            List.of(JAVA, "-jar", ROOT.resolve("cli/build/starter-cli-runner.jar").toString()));
    command.addAll(List.of(args));
    Path out = dir.resolve(UUID.randomUUID() + ".out"),
        err = dir.resolve(UUID.randomUUID() + ".err");
    var process =
        builder(command, env).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
    try {
      assertTrue(process.waitFor(35, TimeUnit.SECONDS), "CLI timeout");
      return new Result(process.exitValue(), Files.readString(out), Files.readString(err));
    } finally {
      process.destroyForcibly();
    }
  }

  static int port() throws java.io.IOException {
    try (var socket = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }
}
