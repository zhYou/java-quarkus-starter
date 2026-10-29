package io.github.zhyou.starter.testing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class PostgresResource implements QuarkusTestResourceLifecycleManager {
  private DatabaseEnvironment db;

  public Map<String, String> start() {
    db = PostgresEnvironment.start();
    return db.applicationConfig();
  }

  public void stop() {
    if (db != null) db.close();
  }
}
