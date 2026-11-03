package io.github.zhyou.starter.buildlogic;

import org.gradle.api.*;
import org.gradle.api.attributes.*;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.toolchain.*;

public final class LocalDevelopmentPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(JavaBasePlugin.class);
    var launcher =
        project
            .getExtensions()
            .getByType(JavaToolchainService.class)
            .launcherFor(
                project.getExtensions().getByType(JavaPluginExtension.class).getToolchain());
    var runtime =
        project
            .getConfigurations()
            .create(
                "maintenanceRuntime",
                c -> {
                  c.setCanBeConsumed(false);
                  c.getAttributes()
                      .attribute(
                          Usage.USAGE_ATTRIBUTE,
                          project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                  c.getAttributes()
                      .attribute(
                          Category.CATEGORY_ATTRIBUTE,
                          project.getObjects().named(Category.class, Category.LIBRARY));
                  c.getAttributes()
                      .attribute(
                          LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                          project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
                });
    project
        .getDependencies()
        .add(
            "maintenanceRuntime",
            project.getDependencies().project(java.util.Map.of("path", ":core")));
    String[][] names = {
      {"doctor", "doctor"},
      {"dbUp", "up"},
      {"dbDown", "down"},
      {"dbReset", "reset"},
      {"dbMigrate", "migrate"},
      {"dbBackup", "backup"},
      {"dbRestore", "restore"},
      {"serena", "serena"}
    };
    var lock =
        project
            .getGradle()
            .getSharedServices()
            .registerIfAbsent(
                "starterLocalEnvironmentLock",
                LocalEnvironmentLock.class,
                spec -> spec.getMaxParallelUsages().set(1));
    for (String[] pair : names)
      project
          .getTasks()
          .register(
              pair[0],
              LocalTask.class,
              task -> {
                task.setGroup("local development");
                task.setDescription("Project-owned local operation: " + pair[1]);
                task.getWorkspace().set(project.getLayout().getProjectDirectory());
                task.getOperation().set(pair[1]);
                task.getDbMode()
                    .set(project.getProviders().gradleProperty("dbMode").orElse("single"));
                task.getDbName().set(project.getProviders().gradleProperty("dbName").orElse(""));
                task.getInputPath().set(project.getProviders().gradleProperty("input").orElse(""));
                task.getOutputPath()
                    .set(project.getProviders().gradleProperty("output").orElse(""));
                task.getConfirmReset()
                    .set(
                        project
                            .getProviders()
                            .gradleProperty("confirmLocalReset")
                            .map("true"::equals)
                            .orElse(false));
                task.getProgramArguments().convention(java.util.List.of());
                task.getJavaExecutable().set(launcher.map(JavaLauncher::getExecutablePath));
                if (java.util.Set.of("up", "migrate", "backup", "restore").contains(pair[1]))
                  task.getMaintenanceClasspath().from(runtime);
                task.usesService(lock);
              });
    project
        .getTasks()
        .register(
            "dev",
            task -> {
              task.setGroup("local development");
              task.setDescription("Run server development mode with the selected local database");
              task.dependsOn(":server:quarkusDev");
            });
    project
        .getTasks()
        .register(
            "runCli",
            LocalCliTask.class,
            task -> {
              task.setGroup("local development");
              task.setDescription(
                  "Run the local CLI; use the Jar directly for native CLI exit codes");
              task.getWorkspace().set(project.getLayout().getProjectDirectory());
              task.getDbMode()
                  .set(project.getProviders().gradleProperty("dbMode").orElse("single"));
              task.getDbName().set(project.getProviders().gradleProperty("dbName").orElse(""));
              task.setClasspath(
                  project.files(
                      project
                          .getLayout()
                          .getProjectDirectory()
                          .file("cli/build/starter-cli-runner.jar")));
              task.dependsOn(":cli:quarkusBuild");
              task.usesService(lock);
            });
  }
}
