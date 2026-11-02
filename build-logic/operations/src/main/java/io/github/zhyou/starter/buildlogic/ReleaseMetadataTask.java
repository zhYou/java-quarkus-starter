package io.github.zhyou.starter.buildlogic;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Verifies the assembled local release on every invocation")
public abstract class ReleaseMetadataTask extends DefaultTask {
  @Internal
  public abstract DirectoryProperty getWorkspace();

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSources();

  @InputFiles
  @PathSensitive(PathSensitivity.NAME_ONLY)
  public abstract ConfigurableFileCollection getArtifacts();

  @Input
  public abstract Property<String> getReleaseVersion();

  @Input
  public abstract Property<String> getCommit();

  @OutputFile
  public abstract RegularFileProperty getMetadata();

  @OutputFile
  public abstract RegularFileProperty getChecksums();

  @TaskAction
  public void write() {
    var target = getMetadata().get().getAsFile().toPath();
    var directory = target.getParent();
    ReleaseFiles.validateSbom(directory.resolve("cli.cdx.json"));
    ReleaseFiles.validateSbom(directory.resolve("server.cdx.json"));
    ReleaseFiles.sourceMetadata(
        getWorkspace().get().getAsFile().toPath(),
        target,
        getReleaseVersion().get(),
        getCommit().get());
    ReleaseFiles.checksums(directory);
    getLogger().lifecycle("Local release artifacts and verified SHA-256 checksums: {}", directory);
  }
}
