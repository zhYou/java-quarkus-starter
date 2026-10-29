# PostgreSQL 与迁移

默认 PostgreSQL 18；运行账户 app_runtime 只有 orders 的 DML 权限，app_migration 拥有 public schema 并执行 Flyway。生产由数据库管理员预先创建这两个角色、授予 schema 权限；应用启动只校验 schema，不自动迁移。

先设置 APP_DB_JDBC_URL、APP_DB_MIGRATION_USERNAME、APP_DB_MIGRATION_PASSWORD，再执行 `./gradlew :core:migrate`。单库 APP_DB_MODE=single。迁移任务是独立 Java 入口，不启动 ORM，可用于空库。

应用读取 APP_DB_USERNAME、APP_DB_PASSWORD。不要把迁移或管理密码注入服务容器。托管数据库的 TLS 要求见云部署说明。迁移失败不会打印配置中的凭据。

V1 创建订单，V2 为已有记录填入 NEW。组合主键包含 customer_id；读取和更新必须同时传客户与订单 ID。条件更新只允许一次 NEW 到 COMPLETED，事务仅承诺同一客户。

测试使用隔离的真实 PostgreSQL 容器和动态端口；Docker 不可用时测试失败，不跳过。`./gradlew :server:test --tests '*PostgresOrderRepositoryTest' --tests '*MigrationTest'` 验证迁移、回滚、竞争和运行权限。

官方说明：[Hibernate schema 校验](https://quarkus.io/guides/hibernate-orm/)、[Flyway](https://quarkus.io/guides/flyway/)。
