# Java Quarkus Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付由智能体维护的 Java Quarkus 多模块模板，提供独立 CLI、HTTP 服务、普通 PostgreSQL 和可验证的 Citus 分片示例。

**Architecture:** core 封装领域、用例、持久化及迁移，cli 和 server 分别处理入口。相同业务契约分别运行于普通 PostgreSQL 和 Citus；本地集群、维护命令与测试相互配套。

**Tech Stack:** Java 25、Quarkus 3.40 LTS、Gradle Wrapper 与 Kotlin DSL、Picocli、Hibernate ORM、Flyway、PostgreSQL 18、Citus 14、mise、Docker Compose、JUnit、Testcontainers、GitHub Actions。

**Spec:** [已批准设计](../specs/2026-10-29-java-quarkus-starter-design.md)。状态：用户已批准并选择当前会话逐项实施。8 项已实施，独立审阅重要问题已修复并通过完整验证；实施阶段未逐步提交；随后按用户单独授权创建首版完整提交并推送，证据见 docs/verification.md 与本地 ledger。

## Global Constraints

- 模块固定为 core、cli、server；cli 和 server 依赖 core，二者相互独立。
- Java 25 LTS；Temurin 为默认发行版。
- Quarkus 3.40 LTS 系列，当前候选补丁 3.40.1.1；平台 BOM 与 Gradle 插件同步。
- Wrapper 优先验证 Gradle 9.8.2，兼容失败时按设计验证官方推荐的 9.7.0 并记录原因。
- PostgreSQL 18 系列与支持它的 Citus 14 系列成套验证，最终固定补丁及镜像摘要。
- 项目 JDK 由 Gradle Toolchains 和 Daemon JVM 条件管理；mise 不安装或切换 Java。
- 构建与测试使用 ./gradlew；mise 任务仅封装这些命令及本地环境操作。
- Java 包根固定为 io.github.zhyou.starter，初始项目版本定为 0.1.0-SNAPSHOT。
- 生产应用默认不在启动时修改 schema，迁移与运行使用独立账户。
- CLI 退出码：成功 0、输入无效 2、不存在 3、冲突 4、内部或数据库错误 1。
- HTTP 输入无效、记录缺失和状态冲突分别返回 400、404、409；错误采用 RFC 9457。
- 普通数据库默认启动；Citus 按需启动，一个协调节点与两个工作节点。
- CI 不依赖付费数据库、云凭据或 Serena 服务；第一版以 Linux 为实际验收平台。
- 真实支付、身份提供方、缓存、消息队列、完整前端、Native Image 和生产分布式集群部署不在首版范围。
- 未选择开源许可证；不添加 LICENSE，也不执行 GitHub 远端发布或云资源创建。

## Review Focus

1. 数据库缺失时，help、version 和纯参数错误仍应正确退出；由任务 3 的离线 CLI 测试固定。
2. CLI 在 Quarkus 启动、诊断和数据库失败时，JSON 标准输出仍可解析；由任务 3 和 7 的进程测试固定。
3. 相同订单 id 配不同 customerId，查询与更新应准确定位组合主键；由任务 2 和 5 的真实数据库契约测试固定。
4. 并发完成同一订单时只能有一次状态转换，其余请求得到冲突；由任务 2、4、5 的并发测试固定。
5. 重启、重复初始化和恢复演练不得丢失源环境数据或影响其他 Docker 资源；由任务 5、6 的生命周期测试固定。

## 执行与提交规则

开始实施时读取所选执行技能及 using-git-worktrees 的环境检测要求。本工作区当前无有效 Git 元数据；不得覆盖受保护的 .git 占位目录。Git 元数据可用时按任务提交，否则继续保存与验证文件，并如实记录未提交状态。

每个功能任务先编写行为测试并确认失败，再实现并运行该任务的验证。配置、文档和工具版本不编写镜像式测试，使用实际命令验证。提交步骤以本任务 Files 清单为范围，保留其他人的改动。

计划按任务 1 至 8 顺序执行。接口和数据库契约依赖较紧，推荐由当前会话逐项实现，最后独立审阅；用户也可以选择每任务独立实现与审阅。

## 文件结构与职责

| 路径 | 职责 |
|---|---|
| settings.gradle.kts、build.gradle.kts、gradle.properties、gradle/libs.versions.toml、gradle/ | 模块、公共构建约定、版本、Wrapper、JDK 和依赖校验 |
| mise.toml、mise.lock、.gitignore、.editorconfig | 辅助工具固定、任务别名和本地文件边界 |
| core/src/main/java/io/github/zhyou/starter/domain/ | 无框架依赖的订单模型、主键、状态与错误 |
| core/src/main/java/io/github/zhyou/starter/application/ | 用例、仓储契约与时间来源 |
| core/src/main/java/io/github/zhyou/starter/persistence/ | JPA 映射及仓储实现 |
| core/src/main/java/io/github/zhyou/starter/maintenance/ | 不启动 Quarkus 的迁移及样例数据传输入口 |
| core/src/main/resources/db/migration/ | 公共与 Citus 专用 Flyway 资源 |
| core/src/testFixtures/java/io/github/zhyou/starter/testing/ | 数据库生命周期、固定数据与共享契约 |
| cli/src/ | Picocli 启动、命令、输出转换和 CLI 测试 |
| server/src/ | REST、Web、管理能力和数据库/API/产物测试 |
| infra/postgres/、infra/citus/ | 两种本地环境、角色与拓扑初始化 |
| scripts/dev.py、scripts/tests/ | 本地生命周期编排、恢复演练和操作边界测试 |
| deploy/、.github/ | OCI、Kubernetes、CI、依赖升级与发布工作流 |
| README.md、AGENTS.md、docs/ | 使用、维护、部署、选型与升级文档 |

以下任务中的 Java 文件均放在上述包根下；Files 使用模块相对路径标明准确类名。其他补丁版本选择留给实施者，但必须写入版本文件和兼容记录。

### Task 1: 构建基线与共享业务契约

**Files:** 创建根构建与工具配置、gradlew、gradlew.bat、gradle/wrapper/、gradle/gradle-daemon-jvm.properties；创建 core/build.gradle.kts、cli/build.gradle.kts、server/build.gradle.kts。

创建 core/src/main/java/io/github/zhyou/starter/domain/{Order,OrderKey,OrderStatus,OrderErrorCode,OrderException}.java，application/{OrderRepository,OrderService,ClockProducer}.java；测试为 core/src/test/java/io/github/zhyou/starter/application/OrderServiceTest.java 和 architecture/DomainBoundaryTest.java。创建 README.md、AGENTS.md、docs/compatibility.md 的初始内容。

**Interfaces:**
- Produces: `OrderKey(UUID customerId, UUID id)`，`Order(UUID customerId, UUID id, String description, OrderStatus status, Instant createdAt)`；状态 NEW、COMPLETED。
- Produces: `OrderRepository.insert(Order): void`、`findByKey(OrderKey): Optional<Order>`、`listByCustomer(UUID,int): List<Order>`、`completeIfNew(OrderKey): boolean`。
- Produces: `OrderService(OrderRepository,Clock)`，`create(UUID,String): Order`、`get(OrderKey): Order`、`list(UUID,int): List<Order>`、`complete(OrderKey): Order`。
- Produces: 错误码 INVALID_INPUT、ORDER_NOT_FOUND、ORDER_CONFLICT、INTERNAL_ERROR，`OrderException.code(): OrderErrorCode`；description 去除首尾空白后长度 1–200，list limit 为 1–100，默认 20，列表按 createdAt、id 升序。
- Produces: `./gradlew :core:test`，JDK 25 测试运行器、固定的工具配置与版本记录。

- [x] **Step 1: 建立实际可用的 Wrapper 和三个构建模块。** 验证 Quarkus BOM/插件、JDK 25、选定 Gradle 能解析并编译；固定格式化、静态检查和测试依赖。配置 Toolchains 下载及 Daemon JVM 条件，记录真实版本和镜像候选。不要修改宿主的全局 mise Java 配置。
- [x] **Step 2: 写失败测试。** 使用固定 Clock 与内存仓储测试替身，断言：

~~~java
assertEquals(NEW, service.create(customerId, " example ").status());
assertEquals("example", service.create(customerId, " example ").description());
assertEquals(fixedInstant, service.create(customerId, "example").createdAt());
assertEquals(INVALID_INPUT, assertThrows(OrderException.class, () -> service.create(customerId, " ")).code());
assertEquals(INVALID_INPUT, assertThrows(OrderException.class, () -> service.list(customerId, 101)).code());
assertEquals(ORDER_NOT_FOUND, assertThrows(OrderException.class, () -> service.get(missingKey)).code());
assertEquals(ORDER_CONFLICT, assertThrows(OrderException.class, () -> service.complete(completedKey)).code());
~~~

DomainBoundaryTest 使用 ArchUnit 断言 domain 不依赖 application、persistence、cli、server 或 Jakarta/Quarkus 框架。
- [x] **Step 3: 运行失败测试。** 执行 `./gradlew :core:test`；预期模型和用例尚未实现导致编译或断言失败，记录实际原因。
- [x] **Step 4: 实现上述模型和接口。** domain 使用普通 Java；OrderService 处理输入、错误和状态规则，时间由 Clock 提供。CDI/事务注解放在 application 或 persistence。通过条件更新的结果处理竞争。
- [x] **Step 5: 验证业务与环境。** 执行 `./gradlew :core:test javaToolchains` 和 `mise install`、`mise exec -- jq --version`、`mise exec -- ast-grep --version`；确认编译与测试目标为 Java 25，mise 配置无 Java 安装项。Serena 可独立检查 help，不启动 MCP 服务作为构建条件。
- [ ] **Step 6: 提交。** 验证后提交本任务文件及已批准设计、实施计划，消息为 `build: establish Java 25 multi-module baseline`；Git 不可用时记录未提交。

### Task 2: PostgreSQL 持久化、角色与迁移

**Files:** 创建 core 的 persistence/{OrderEntity,OrderEntityId,JpaOrderRepository}.java、maintenance/{DatabaseMode,MigrationConfig,MigrationRunner}.java、META-INF/beans.xml、db/migration/common/{V1__create_orders,V2__add_order_status}.sql。

创建测试夹具 testing/{DatabaseEnvironment,PostgresEnvironment,OrderRepositoryContract}.java；创建 server 的测试 persistence/{PostgresOrderRepositoryTest,MigrationTest}.java；创建 infra/postgres/{compose.yaml,init/001_roles.sh}。更新构建、README.md、AGENTS.md，创建 docs/database.md。

**Interfaces:**
- Consumes: Task 1 的 Order、OrderKey、OrderRepository 和 OrderService。
- Produces: `JpaOrderRepository implements OrderRepository`，组合主键映射 customer_id、id，created_at 对应 UTC Instant。
- Produces: `DatabaseMode` 的外部值 single、sharding；`MigrationConfig(DatabaseMode mode,String jdbcUrl,String username,String password)`；`MigrationRunner.migrate(MigrationConfig): void`、`main(String[]): void`，无需启动 Quarkus。
- Produces: `DatabaseEnvironment extends AutoCloseable`，暴露 `jdbcUrl(): String`、`runtimeUsername(): String`、`runtimePassword(): String`、`migrationUsername(): String`、`migrationPassword(): String`。
- Produces: `PostgresEnvironment.start(): DatabaseEnvironment` 为测试创建隔离环境，close() 仅清理该实例拥有的资源。
- Produces: Gradle `:core:migrate`，环境变量 APP_DB_MODE、APP_DB_JDBC_URL、APP_DB_USERNAME、APP_DB_PASSWORD、APP_DB_MIGRATION_USERNAME、APP_DB_MIGRATION_PASSWORD。

- [x] **Step 1: 写真实 PostgreSQL 失败测试。** 仓储契约断言创建/读取、同 id 不同 customerId 隔离、稳定排序和上限、事务抛错后回滚；并发 complete 操作成功数为 1。MigrationTest 断言空库迁移、重复执行和 V1 带记录升级 V2 后 status 为 NEW。
- [x] **Step 2: 运行失败测试。** 执行 `./gradlew :server:test --tests '*PostgresOrderRepositoryTest' --tests '*MigrationTest'`；预期缺少仓储或迁移导致失败，不因 Docker 缺失静默跳过。
- [x] **Step 3: 实现映射、迁移和测试环境。** V1 创建基础订单字段与组合主键，V2 添加 status 并回填 NEW。条件更新只允许 NEW 到 COMPLETED；事务中所有写入通过参数绑定执行。为公共库添加 Bean 发现资源。
- [x] **Step 4: 实现权限和配置。** 管理初始化建立 app_runtime 与 app_migration 角色；运行账户可读写业务数据但不能建表，迁移账户拥有必要 DDL 权限。生产关闭启动迁移并校验 schema；测试在启动应用前完成迁移。凭据只通过环境或受忽略文件传递。
- [x] **Step 5: 验证真实行为。** 重跑上述测试和 `./gradlew :core:test`；额外断言运行账户 INSERT 成功而 CREATE TABLE 失败，空库迁移不依赖 ORM 启动，数据库不可用错误不包含密码。数据库启动与迁移步骤写入文档。
- [ ] **Step 6: 提交。** 提交本任务 Files，消息为 `feat: add PostgreSQL persistence and migrations`。

### Task 3: 独立 CLI

**Files:** 创建 cli/src/main/java/io/github/zhyou/starter/cli/{StarterCli,CliApplication,RootCommand,OrdersCommand,CreateOrderCommand,GetOrderCommand,ListOrdersCommand,CompleteOrderCommand,CliOutput,CliErrors}.java、cli/src/main/resources/application.properties；创建 cli/src/test/java/io/github/zhyou/starter/cli/{CliOfflineTest,CliOrdersTest,CliFailureTest}.java。更新 README.md、AGENTS.md。

**Interfaces:**
- Consumes: Task 1 用例、错误码和 Task 2 测试环境及 JDBC 配置。
- Produces: `StarterCli.main(String[]): void`、`CliApplication.run(String...): int`。
- Produces: 命令名 starter，根选项 --output=text|json，命令 orders create/get/list/complete；读取和完成要求 --customer-id 与 --id，创建要求 --customer-id 与 --description，列表使用 --customer-id、--limit。
- Produces: `CliOutput.writeSuccess(Object): void`、`writeFailure(OrderErrorCode,String): void`；退出码遵循设计，Jackson 输出 Instant 为 UTC 文本。

- [x] **Step 1: 写离线失败测试。** 移除数据库配置，断言根和 orders 子命令 help、version 返回 0；错误 UUID、空 description、limit=101 返回 2。这些场景不得启动数据库连接。
- [x] **Step 2: 写业务失败测试。** 真实数据库中 create/get/list/complete 结果一致；不存在返回 3，重复完成返回 4。JSON 标准输出只能解析为一个完整 JSON 值；数据库失败返回 1，诊断只在标准错误且不含密码或 SQL 堆栈。
- [x] **Step 3: 确认失败。** 执行 `./gradlew :cli:test`；预期缺少启动入口或命令实现。
- [x] **Step 4: 实现两阶段入口。** 用不启动 Quarkus 的 Picocli 解析处理 help/version 和语法错误，再由 Quarkus/CDI 执行业务命令。禁止 Quarkus banner 与日志污染 JSON 标准输出。默认文本输出，业务规则只调用 OrderService。
- [x] **Step 5: 验证和打包。** 执行 `./gradlew :cli:test :cli:quarkusBuild`；CLI 使用 uber-jar，固定路径 cli/build/starter-cli-runner.jar。执行 `java -jar cli/build/starter-cli-runner.jar --help`，预期退出 0，且无需数据库配置；记录最终参数传递命令。
- [ ] **Step 6: 提交。** 消息为 `feat: add standalone orders CLI`。

### Task 4: HTTP API、轻量 Web 和服务运行能力

**Files:** 创建 server/src/main/java/io/github/zhyou/starter/api/{OrderResource,CreateOrderRequest,OrderResponse,ProblemResponse,OrderExceptionMapper,RequestIdFilter}.java、server/src/main/java/io/github/zhyou/starter/web/DemoWebResource.java、server/src/main/resources/application.properties；创建 server/src/test/java/io/github/zhyou/starter/api/{OrderResourceTest,OrderErrorsTest,ConcurrentOrderUpdateTest}.java、server/src/test/java/io/github/zhyou/starter/web/DemoWebTest.java、server/src/test/java/io/github/zhyou/starter/operations/HealthTest.java。更新 README.md、AGENTS.md，创建 docs/api.md。

**Interfaces:**
- Consumes: Task 1 用例与 Task 2 的持久化和测试环境。
- Produces: POST /api/v1/customers/{customerId}/orders，body description；GET 同路径列表，query limit 默认 20；GET /api/v1/customers/{customerId}/orders/{id}；POST 同一订单路径的 /complete。
- Produces: 创建返回 201 与 Location，读取/列表/完成返回 200；ProblemResponse 包含 type、title、status、detail、instance、code、requestId。
- Produces: 每个响应包含服务端生成的 X-Request-ID；/web/demo 返回最小 HTML，/web/redirect 返回 303 到固定本地 /web/demo。
- Produces: 业务端口 8080、管理端口 9000；管理路径 /q/health/live、/q/health/ready、/q/metrics，均可通过配置覆盖。

- [x] **Step 1: 写失败测试。** 覆盖 201/200/400/404/409、组合主键隔离、列表上限和顺序；并发完成仅一次 200，其余 409。断言错误为 application/problem+json、有稳定 code 和 requestId，无堆栈、SQL 或凭据。
- [x] **Step 2: 写 Web 与运行失败测试。** HTML 能访问；redirect 的 Location 固定，外部 target 参数不改变目标；数据库停用时 readiness 失败而 liveness 保持进程存活语义；默认不连接外部遥测采集器。
- [x] **Step 3: 确认失败。** 执行 `./gradlew :server:test`，预期接口及管理配置缺失导致失败。
- [x] **Step 4: 实现入口与运行配置。** REST/Jackson/Validator 调用同一 OrderService；按设计映射错误。开发日志可读、生产 JSON 日志包含请求关联字段；Swagger UI 默认仅开发和测试开启。使用 SmallRye Health、Micrometer 与可选 OpenTelemetry，管理接口独立配置。
- [x] **Step 5: 验证。** 执行 `./gradlew :server:test :server:quarkusBuild`；server 使用 fast-jar，运行入口 server/build/quarkus-app/quarkus-run.jar，分发时必须带完整 quarkus-app 目录。验证 core Bean 在应用中可发现。
- [ ] **Step 6: 提交。** 消息为 `feat: add HTTP orders service and operational endpoints`。

### Task 5: 可运行的 Citus 分片环境

**Files:** 创建 infra/citus/{compose.yaml,init/001_roles.sh,init/002_topology.sql}、core/src/main/resources/db/migration/citus/R__distribute_orders.sql、core/src/testFixtures/java/io/github/zhyou/starter/testing/CitusEnvironment.java；创建 server/src/test/java/io/github/zhyou/starter/sharding/{CitusOrderRepositoryTest,CitusRoutingTest,CitusLifecycleTest}.java 和 cli/src/test/java/io/github/zhyou/starter/cli/CitusCliTest.java。创建 docs/sharding.md，更新构建及任务文档。

**Interfaces:**
- Consumes: 普通数据库的公共迁移、角色契约、用例、仓储测试和两种应用入口。
- Produces: 一个 coordinator、两个 worker；测试使用独立 Docker Network 和动态宿主端口，手工示例协调端口默认 55432。
- Produces: `CitusEnvironment.start(): CitusEnvironment`，实现 DatabaseEnvironment，额外暴露 `workerJdbcUrls(): List<String>`，供测试使用迁移账户核验物理数据。
- Produces: 订单表使用 customer_id 分布键，示例固定 8 个逻辑分片；重复初始化跳过已有正确拓扑，对不兼容现存拓扑明确失败。
- Produces: `:server:shardingTest`、`:cli:shardingTest` 与根任务 `shardingCheck`。公共迁移先运行，Citus 分布表初始化使用 repeatable 迁移并检查现有元数据。

- [x] **Step 1: 写失败契约。** 复用 OrderRepositoryContract，测试同 customerId 事务回滚、相同 id 不同客户、并发完成，以及 CLI 在分片配置下的 create/get/complete。
- [x] **Step 2: 写真实路由与生命周期失败测试。** 固定客户集合写入订单，查询 Citus 元数据并直接连接两个 worker，断言两个节点均实际存储不同记录、合计数正确、同客户记录共置；重启保留记录，重复 up 和 migrate 不丢数据。
- [x] **Step 3: 确认失败。** 执行 `./gradlew shardingCheck`，预期缺少 Citus 环境或分布表。即使单节点 SQL 返回正确结果，路由测试也必须失败。
- [x] **Step 4: 实现拓扑与初始化。** 固定可用的 PostgreSQL 18/Citus 14 镜像；在各节点建立必要角色，配置节点间认证，注册两个 worker。Citus 扩展/节点初始化由管理阶段执行，业务应用使用运行账户。
- [x] **Step 5: 验证兼容与资源。** 执行 `./gradlew shardingCheck`；每个作业最多运行一套集群，server 与 cli 的分片测试顺序执行，测试 maxParallelForks 为 1。重复运行仍通过，所有数据库写入断言基于真实节点。记录选择的兼容镜像与分布表限制。
- [ ] **Step 6: 提交。** 消息为 `feat: add executable Citus sharding example`。

### Task 6: 本地任务、迁移编排和恢复演练

**Files:** 创建 scripts/dev.py、scripts/tests/{test_dev_tasks,test_lifecycle,test_restore}.py、core/src/main/java/io/github/zhyou/starter/maintenance/{SampleDataTransfer,ExportBundle}.java、core/src/test/java/io/github/zhyou/starter/maintenance/SampleDataTransferTest.java、server/src/test/java/io/github/zhyou/starter/maintenance/SampleDataRecoveryTest.java；创建 docs/operations.md、.env.example。更新 mise.toml、忽略规则、README.md、AGENTS.md。

**Interfaces:**
- Consumes: Task 2 的 MigrationRunner、APP_DB_* 契约与 Task 5 的拓扑。
- Produces: Python 入口 `main(argv: list[str]) -> int`，子命令 doctor/up/down/reset/migrate/export/restore，--mode=single|sharding；reset 必须提供 --confirm-local-reset。
- Produces: Gradle `:core:migrate`、`:core:sampleExport`、`:core:sampleImport`；后两者仅处理本模板订单数据，`SampleDataTransfer.exportData(Connection,Path): void`、`importData(Connection,Path): void`，ExportBundle 包含 formatVersion=1 和完整 Order 列表。
- Produces: .local/db-single.env、.local/db-sharding.env 为本地自动生成的受忽略配置，文件权限限制；示例配置无有效生产凭据。Compose 项目名和资源归属写入项目本地状态。
- Produces: mise 的 doctor、db:up/down/reset、sharding:up/down/reset、migrate、dev、cli、check、sharding:check、package:check、db:backup、db:restore、sharding:export、sharding:restore。
- Produces: `:server:recoveryTest` 专门运行标记 recovery 的 SampleDataRecoveryTest，依次覆盖 single 与 sharding，并加入根 shardingCheck。

- [x] **Step 1: 写操作边界失败测试。** 未确认的 reset 返回非零；未知模式、非项目环境或外部数据库 URL 拒绝重置；down 保留卷；调用命令绝不包含全局 prune。工具缺失时 doctor 给出明确修复信息且不泄漏配置。
- [x] **Step 2: 写恢复失败测试并登记 recoveryTest 任务。** SampleDataTransferTest 验证 UUID、UTC 时间、文本和状态保留，以及损坏文件或未知 formatVersion 在插入前失败。SampleDataRecoveryTest 标记 recovery，验证普通库和 Citus 都恢复到新的本地目标环境，数据相同且源环境记录仍在。
- [x] **Step 3: 确认失败。** 执行 `python3 -m unittest discover -s scripts/tests -v` 和 `./gradlew :core:test --tests '*SampleDataTransferTest' :server:recoveryTest --tests '*SampleDataRecoveryTest'`；预期已被测试发现的脚本或恢复行为缺失导致失败。
- [x] **Step 4: 实现编排。** Python 只使用标准库，以 argv 数组执行子进程；构建和迁移调用 Wrapper。初始化产生本地开发凭据但不打印，启动自动串联迁移。down 和 reset 的行为分开，资源操作只使用项目记录的 Compose 资源。
- [x] **Step 5: 实现恢复路径。** 普通 PostgreSQL 使用匹配版本的 pg_dump/pg_restore 演练新库恢复；Citus 停写示例由样例数据导出、新拓扑初始化、迁移、导入构成。失败目标保留诊断，源环境和连接配置不自动替换。
- [x] **Step 6: 验证。** 重跑 Python 和恢复测试；实际执行 `mise run doctor`、两种 up/down，再次启动核验数据保留。恢复测试加入 shardingCheck，并在失败时清理测试拥有的容器。运维文档区分样例恢复与生产备份。
- [ ] **Step 7: 提交。** 消息为 `feat: add reproducible local database operations`。

### Task 7: 产物、工程检查与 CI

**Files:** 创建 server/src/test/java/io/github/zhyou/starter/packaged/{CliArtifactTest,ServerArtifactTest}.java、deploy/docker/{Dockerfile.cli,Dockerfile.server}、.dockerignore、.github/workflows/ci.yml；更新公共构建配置、版本锁与校验文件、README.md、AGENTS.md。

**Interfaces:**
- Consumes: 已完成的两个应用、两种数据库环境和独立迁移入口。
- Produces: 根任务 `check`、`shardingCheck`、`packageCheck`；packageCheck 依赖两个应用的 quarkusBuild 及 `:server:packageTest`，使用 Java 25 启动真实产物，顺序覆盖 single 与 sharding。
- Produces: 本地镜像 starter-cli:test、starter-server:test；CLI 作为一次性任务退出，server 监听业务和内部管理端口。
- Produces: CI 的 standard、sharding、packaged 作业与失败报告，均不依赖云或 MCP。

- [x] **Step 1: 写打包产物失败测试并登记 packageTest/packageCheck 任务。** 两个测试类标记 packaged，通过 ProcessBuilder 启动真实文件，断言 CLI 离线 help/version 与 JSON 输出；在两个数据库模式下分别完成 CLI 创建、HTTP 读取和状态更新，证明共享 Bean、配置和驱动随产物存在。
- [x] **Step 2: 确认失败。** 执行 `./gradlew packageCheck`；若仅开发模式可用、依赖未打包或 CLI 输出混入日志，测试必须失败。
- [x] **Step 3: 固定质量入口。** 将格式验证、静态分析和领域边界测试接入 check；普通 test 排除 sharding、recovery、packaged 标签。shardingCheck 顺序运行 server:shardingTest、cli:shardingTest 和 server:recoveryTest；packageCheck 单独运行 packageTest，避免普通检查意外启动多节点环境。
- [ ] **Step 4: 实现 OCI 与 CI。** 构建任务固定基础镜像摘要。server 以非 root 运行并支持 SIGTERM；CLI 使用 exec 入口传递退出码。CI 使用固定 Action 提交、最小权限、Wrapper 校验、依赖缓存、作业超时和失败报告，清理本作业资源。
- [x] **Step 5: 验证。** 顺序执行 `./gradlew check shardingCheck packageCheck`，再执行两个 Dockerfile 的 docker build 和相应 docker run 冒烟。HTTP 产物 SIGTERM 后在配置期限内退出，CLI 退出码与 JSON 契约不变。actionlint 校验工作流，记录实际本地结果。
- [ ] **Step 6: 提交。** 消息为 `ci: verify packaged applications and database modes`。

### Task 8: 云原生部署、版本治理与交付说明

**Files:** 创建 deploy/kubernetes/{deployment,service}.yaml、.github/workflows/release.yml、.github/dependabot.yml、docs/{cloud-deployment,upgrading,database-options,extensions}.md；完善 README.md、AGENTS.md、docs/compatibility.md 与 docs/operations.md。

**Interfaces:**
- Consumes: server 镜像、8080/9000 端口、APP_DB_* 配置、健康端点、CLI 产物和已验证命令。
- Produces: 通用 Deployment 和业务 Service，Secret 引用提供凭据，管理端口只用于内部探针；示例资源 request 为 CPU 250m/内存 256Mi，limit 为 CPU 1/内存 512Mi，并明确需按负载校准。
- Produces: 标签或手动触发的发布工作流，归档 CLI jar、server 完整运行目录、校验值、依赖清单与源码版本标识；本次只交付配置，不实际远端发布。
- Produces: 三云已有托管 PostgreSQL 接入、版本升级与回退验证、Serena 启动和扩展接入位置的文档。

- [x] **Step 1: 创建部署与治理配置。** Kubernetes 使用业务 Service、内部管理探针、Secret 环境引用和优雅退出；不包含数据库集群或云资源创建。依赖更新维持 Quarkus BOM/插件配套与 LTS 主线。
- [x] **Step 2: 校验交付配置。** kubeconform 校验 Kubernetes 文件；actionlint 校验 CI 与发布工作流；生成并核验两个产物的依赖清单、源码标识和校验值。静态配置不增加镜像式单元测试。
- [x] **Step 3: 完成使用与维护文档。** 从环境引导到两种数据库模式、CLI、API、恢复和升级写出实际命令；AWS/阿里云/腾讯云分别列 TLS、连接池、凭据、版本和备份核验项。明确 API 示例无身份认证及 OIDC 接入位置，说明 MySQL 等备选路线。
- [x] **Step 4: 完成工具说明。** 演示固定 Serena 来源及从当前目录启动的命令；需要 Java 语言服务时复用 Gradle JDK。构建不依赖启动 Serena，不改全局 MCP 或 mise Java 配置。核验 jq、git、ripgrep、ast-grep 及 Python/uv 的固定方式。
- [x] **Step 5: 按快速开始复核。** 使用干净的项目本地状态和新建的项目示例资源复核 README；运行 doctor、check、shardingCheck、packageCheck 与两种恢复演练。区分本地验证、CI 配置校验和未执行的云端验证，保留真实限制。
- [ ] **Step 6: 提交并交付。** 消息为 `docs: complete deployment and maintenance workflows`。汇总改动、验证结果、版本与限制；许可证保持待定。按选择的执行方法进行最终独立审阅，修复有效问题后交付工作区结果。

## 自审覆盖

| 设计要求 | 实施任务 |
|---|---|
| LTS、Wrapper、Gradle JDK、mise、三模块 | 1 |
| 业务规则、事务、仓储、权限、迁移 | 1、2 |
| CLI、API、轻量 Web、错误与运行能力 | 3、4 |
| 真实分片、初始化、路由、重启保留 | 5 |
| 环境生命周期、备份、示例恢复与资源边界 | 6 |
| 质量、产物、OCI 与无云凭据 CI | 7 |
| 三云接入、Kubernetes、版本治理、文档和发布配置 | 8 |

接口自审以 Task 1 的 OrderService/OrderRepository 和 Task 2 的 APP_DB_* 为唯一命名来源。Review Focus 五项分别落到任务 2、3、4、5、6、7 的明确断言。计划只细化已批准范围，新增业务能力或改变数据库/LTS 主线必须作为设计变更处理。
