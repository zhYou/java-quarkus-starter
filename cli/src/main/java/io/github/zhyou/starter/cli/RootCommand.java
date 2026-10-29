package io.github.zhyou.starter.cli;

import picocli.CommandLine.*;

@Command(
    name = "starter",
    mixinStandardHelpOptions = true,
    version = "starter 0.1.0-SNAPSHOT",
    description = "Order template CLI")
@jakarta.enterprise.inject.Vetoed
public class RootCommand {
  enum Output {
    text,
    json
  }

  @Option(
      names = "--output",
      defaultValue = "text",
      scope = ScopeType.INHERIT,
      description = "${COMPLETION-CANDIDATES}")
  Output output;
}
