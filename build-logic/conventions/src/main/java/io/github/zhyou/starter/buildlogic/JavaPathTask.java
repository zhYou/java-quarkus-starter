package io.github.zhyou.starter.buildlogic;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Reports the current managed launcher")
public abstract class JavaPathTask extends DefaultTask {
  @InputFile
  public abstract RegularFileProperty getJavaExecutable();

  @TaskAction
  public void printPath() {
    System.out.println(
        "STARTER_JAVA_PATH=" + getJavaExecutable().get().getAsFile().getAbsolutePath());
  }
}
