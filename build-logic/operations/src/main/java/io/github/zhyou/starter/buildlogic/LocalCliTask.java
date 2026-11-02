package io.github.zhyou.starter.buildlogic;

import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Runs a business command against external state")
public abstract class LocalCliTask extends JavaExec {
  @Internal
  public abstract DirectoryProperty getWorkspace();

  @Input
  public abstract Property<String> getDbMode();

  @Input
  public abstract Property<String> getDbName();

  @Override
  @TaskAction
  public void exec() {
    var local =
        new LocalEnvironment(
            getWorkspace().get().getAsFile().toPath(),
            System.getenv(),
            (command, env, in, out) -> {
              throw new UnsupportedOperationException();
            },
            List.of());
    setEnvironment(local.environment(local.load(getDbMode().get(), getDbName().get())));
    super.exec();
  }
}
