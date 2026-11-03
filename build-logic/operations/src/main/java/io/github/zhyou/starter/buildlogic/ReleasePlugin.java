package io.github.zhyou.starter.buildlogic;

import org.gradle.api.*;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.*;

public final class ReleasePlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    String version = project.getProviders().gradleProperty("version").get();
    var release = project.getLayout().getBuildDirectory().dir("release");
    var server =
        project
            .getTasks()
            .register(
                "archiveReleaseServer",
                Tar.class,
                task -> {
                  task.dependsOn(":server:quarkusBuild");
                  task.getDestinationDirectory()
                      .set(project.getLayout().getBuildDirectory().dir("release-staging"));
                  task.getArchiveFileName().set("starter-server-" + version + ".tar.gz");
                  task.setCompression(Compression.GZIP);
                  task.from(
                      project.getLayout().getProjectDirectory().dir("server/build/quarkus-app"),
                      copy -> copy.into("quarkus-app"));
                });
    var stage =
        project
            .getTasks()
            .register(
                "stageRelease",
                Sync.class,
                task -> {
                  task.dependsOn(":cli:quarkusBuild");
                  task.into(release);
                  task.from(
                      project
                          .getLayout()
                          .getProjectDirectory()
                          .file("cli/build/starter-cli-runner.jar"),
                      copy -> copy.rename(name -> "starter-cli-" + version + ".jar"));
                  task.from(server.flatMap(Tar::getArchiveFile));
                  task.from(
                      project
                          .getLayout()
                          .getProjectDirectory()
                          .file("cli/build/starter-cli-runner-cyclonedx.json"),
                      copy -> copy.rename(name -> "cli.cdx.json"));
                  task.from(
                      project
                          .getLayout()
                          .getProjectDirectory()
                          .file("server/build/quarkus-run-cyclonedx.json"),
                      copy -> copy.rename(name -> "server.cdx.json"));
                });
    var git =
        project
            .getProviders()
            .exec(
                spec -> {
                  spec.commandLine("git", "rev-parse", "HEAD");
                  spec.workingDir(project.getLayout().getProjectDirectory());
                  spec.setIgnoreExitValue(true);
                })
            .getStandardOutput()
            .getAsText()
            .map(String::trim);
    var metadata =
        project
            .getTasks()
            .register(
                "releaseMetadata",
                ReleaseMetadataTask.class,
                task -> {
                  task.dependsOn(stage);
                  task.getWorkspace().set(project.getLayout().getProjectDirectory());
                  task.getReleaseVersion().set(version);
                  task.getCommit()
                      .set(
                          project
                              .getProviders()
                              .environmentVariable("GITHUB_SHA")
                              .orElse(project.getProviders().environmentVariable("SOURCE_COMMIT"))
                              .orElse(git));
                  task.getMetadata().set(release.map(dir -> dir.file("SOURCE.json")));
                  task.getChecksums().set(release.map(dir -> dir.file("SHA256SUMS")));
                  task.getArtifacts()
                      .from(
                          project.fileTree(
                              release, tree -> tree.exclude("SOURCE.json", "SHA256SUMS")));
                  task.getSources()
                      .from(
                          project.fileTree(
                              project.getLayout().getProjectDirectory(),
                              tree -> {
                                for (String source : ReleaseFiles.SOURCE_ROOTS) {
                                  tree.include(source);
                                  tree.include(source + "/**");
                                }
                                tree.exclude(
                                    "**/build/**",
                                    "**/.gradle/**",
                                    "**/.local/**",
                                    "**/__pycache__/**");
                              }));
                });
    project
        .getTasks()
        .register(
            "assembleRelease",
            task -> {
              task.setGroup("distribution");
              task.setDescription("Assemble and verify local release artifacts without publishing");
              task.dependsOn(metadata);
            });
  }
}
