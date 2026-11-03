package io.github.zhyou.starter.buildlogic;

import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;

public abstract class SampleArguments implements CommandLineArgumentProvider {
  @Input
  public abstract Property<String> getOperation();

  @Input
  public abstract Property<String> getDataFile();

  @Override
  public Iterable<String> asArguments() {
    return List.of(getOperation().get(), getDataFile().get());
  }
}
