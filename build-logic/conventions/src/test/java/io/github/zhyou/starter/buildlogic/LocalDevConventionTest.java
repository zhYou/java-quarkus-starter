package io.github.zhyou.starter.buildlogic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDevConventionTest {
  @TempDir Path root;

  @Test
  void localEnvironmentIsAvailableBeforeQuarkusFinalizesItsTaskInputs() throws Exception {
    var local =
        new LocalEnvironment(
            root,
            Map.of(),
            (command, env, in, out) -> {
              throw new AssertionError("No subprocess expected");
            },
            List.of());
    local.create("single", null, 15432);
    String catalog =
        Path.of(System.getProperty("starter.repository"), "gradle/libs.versions.toml")
            .toString()
            .replace('\\', '/');
    Files.writeString(
        root.resolve("settings.gradle"),
        "rootProject.name='local-dev-fixture'\ndependencyResolutionManagement { repositories { mavenCentral() }; versionCatalogs { libs { from(files('"
            + catalog
            + "')) } } }\n");
    Files.writeString(
        root.resolve("build.gradle"),
        """
      plugins { id 'application-conventions' }
      def environment = tasks.named('quarkusDev').get().environmentVariables.get()
      if (environment.APP_DB_JDBC_URL != 'jdbc:postgresql://127.0.0.1:15432/starter') {
          throw new GradleException('Local environment missing before task input finalization')
      }
      if (environment.QUARKUS_DEVSERVICES_ENABLED != 'false') {
          throw new GradleException('Local mode must only use project-owned Compose services')
      }
      tasks.register('verifyLocalDev') { doLast { println 'LOCAL_DEV_ENV_OK' } }
      """);
    var env = new HashMap<>(System.getenv());
    env.keySet().removeIf(k -> k.startsWith("APP_DB_"));
    env.put("JAVA_HOME", System.getProperty("java.home"));
    var arguments =
        new ArrayList<>(
            List.of(
                "verifyLocalDev",
                "-PlocalDb=true",
                "-g",
                System.getProperty("starter.gradleUserHome")));
    if (Boolean.getBoolean("starter.offline")) arguments.add("--offline");
    var result =
        GradleRunner.create()
            .withProjectDir(root.toFile())
            .withPluginClasspath()
            .withEnvironment(env)
            .withArguments(arguments)
            .build();
    assertTrue(result.getOutput().contains("LOCAL_DEV_ENV_OK"));
  }
}
