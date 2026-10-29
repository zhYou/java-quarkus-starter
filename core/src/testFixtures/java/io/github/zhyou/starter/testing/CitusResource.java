package io.github.zhyou.starter.testing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class CitusResource implements QuarkusTestResourceLifecycleManager {
  private CitusEnvironment db;

  public Map<String, String> start() {
    db = CitusEnvironment.start();
    return db.applicationConfig();
  }

  public void stop() {
    if (db != null) db.close();
  }
}
