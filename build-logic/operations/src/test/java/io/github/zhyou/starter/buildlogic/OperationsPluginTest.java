package io.github.zhyou.starter.buildlogic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperationsPluginTest {
  @TempDir Path root;

  GradleRunner runner(String... args) throws Exception {
    Files.createDirectories(root.resolve("core"));
    Files.writeString(
        root.resolve("settings.gradle"), "rootProject.name='fixture'\ninclude 'core'\n");
    Files.writeString(root.resolve("core/build.gradle"), "plugins { id 'java-library' }\n");
    Files.writeString(root.resolve("build.gradle"), "plugins { id 'starter.local-development' }\n");
    var env = new HashMap<>(System.getenv());
    env.keySet().removeIf(k -> k.startsWith("APP_DB_"));
    env.put("JAVA_HOME", System.getProperty("java.home"));
    env.put(
        "PATH", root.resolve("bin") + java.io.File.pathSeparator + env.getOrDefault("PATH", ""));
    var arguments = new ArrayList<>(List.of(args));
    arguments.addAll(List.of("-g", System.getProperty("starter.gradleUserHome")));
    return GradleRunner.create()
        .withProjectDir(root.toFile())
        .withPluginClasspath()
        .withEnvironment(env)
        .withArguments(arguments);
  }

  @Test
  void unconfirmedResetFailsBeforeStateOrSubprocessCreation() throws Exception {
    var result = runner("dbReset", "--configuration-cache", "--offline").buildAndFail();
    assertTrue(
        result.getOutput().contains("reset requires -PconfirmLocalReset=true"), result.getOutput());
    assertFalse(Files.exists(root.resolve(".local")));
  }

  @Test
  void serenaKeepsProtocolOutputManagedJdkAndArgumentsAcrossCacheReuse() throws Exception {
    Path executable = root.resolve("bin/serena");
    Files.createDirectories(executable.getParent());
    Files.writeString(
        executable,
        "#!/bin/sh\nprintf '%s\\n' \"$JAVA_HOME\" > serena-home.txt\nprintf '%s\\n' \"$@\" > serena-args.txt\nprintf 'protocol-marker\\n'\n");
    Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
    for (int i = 0; i < 2; i++) {
      var result =
          runner(
                  "-q",
                  "serena",
                  "--configuration-cache",
                  "--offline",
                  "--args=--context 'project space'")
              .build();
      assertEquals("protocol-marker", result.getOutput().trim());
      assertEquals(
          Path.of(System.getProperty("java.home")).toRealPath(),
          Path.of(Files.readString(root.resolve("serena-home.txt")).trim()).toRealPath());
      assertEquals(
          List.of(
              "start-mcp-server",
              "--project-from-cwd",
              "--open-web-dashboard",
              "false",
              "--context",
              "project space"),
          Files.readAllLines(root.resolve("serena-args.txt")));
      assertFalse(Files.exists(root.resolve(".local")));
    }
  }
}
