package io.github.zhyou.starter.buildlogic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConventionsTest {
  @TempDir Path root;

  GradleRunner runner(String... arguments) throws Exception {
    String catalog =
        Path.of(System.getProperty("starter.repository"), "gradle/libs.versions.toml")
            .toString()
            .replace("\\", "/");
    Files.writeString(
        root.resolve("settings.gradle"),
        "rootProject.name='fixture'\ndependencyResolutionManagement { repositories { mavenCentral() }; versionCatalogs { libs { from(files('"
            + catalog
            + "')) } } }");
    Files.writeString(
        root.resolve("build.gradle"), "plugins { id 'starter.java-library-conventions' }\n");
    var env = new HashMap<>(System.getenv());
    env.put("JAVA_HOME", System.getProperty("java.home"));
    var args = new ArrayList<>(List.of(arguments));
    if (Boolean.getBoolean("starter.offline")) args.add("--offline");
    args.addAll(List.of("-g", System.getProperty("starter.gradleUserHome")));
    Files.createDirectories(root.resolve("gradle"));
    Files.copy(
        Path.of(System.getProperty("starter.repository"), "gradle/verification-metadata.xml"),
        root.resolve("gradle/verification-metadata.xml"),
        StandardCopyOption.REPLACE_EXISTING);
    return GradleRunner.create()
        .withProjectDir(root.toFile())
        .withPluginClasspath()
        .withEnvironment(env)
        .withArguments(args);
  }

  @Test
  void compilesJavaAndReusesConfigurationCache() throws Exception {
    Path source = root.resolve("src/main/java/Example.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "public record Example(String name) {}\n");
    var first =
        runner("compileJava", "--configuration-cache", "-Pgroup=fixture", "-Pversion=1").build();
    assertTrue(
        Files.exists(root.resolve("build/classes/java/main/Example.class")), first.getOutput());
    var second =
        runner("compileJava", "--configuration-cache", "-Pgroup=fixture", "-Pversion=1").build();
    assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());
  }

  @Test
  void compilerWarningsFailTheBuild() throws Exception {
    Path source = root.resolve("src/main/java/Bad.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "class Bad { java.util.List raw; }\n");
    var result = runner("compileJava", "-Pgroup=fixture", "-Pversion=1").buildAndFail();
    assertTrue(
        result.getOutput().contains("warnings found and -Werror specified"), result.getOutput());
  }
}
