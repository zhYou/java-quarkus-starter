package io.github.zhyou.starter.buildlogic;

import java.io.File;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Callable;

/** Supplies local configuration before Quarkus finalizes its development task inputs. */
public final class LocalDevEnvironment implements Callable<Map<String, String>>, Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final File root;
  private final String mode;
  private final String name;

  public LocalDevEnvironment(File root, String mode, String name) {
    this.root = root;
    this.mode = mode;
    this.name = name;
  }

  @Override
  public Map<String, String> call() {
    var local =
        new LocalEnvironment(
            root.toPath(),
            System.getenv(),
            (command, env, input, output) -> {
              throw new UnsupportedOperationException(
                  "No subprocess needed for environment loading");
            },
            List.of());
    var environment = local.environment(local.load(mode, name));
    environment.keySet().removeIf(key -> !key.startsWith("APP_DB_"));
    environment.put("QUARKUS_DEVSERVICES_ENABLED", "false");
    return environment;
  }
}
