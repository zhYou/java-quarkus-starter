# Java Quarkus Starter

Java 25 LTS、Quarkus 3.40 LTS、Gradle Wrapper 多模块模板。core 共享订单业务和持久化；cli 是独立命令行程序；server 提供 API、最小 HTML 和固定本地跳转。默认 PostgreSQL，另有可运行的 Citus 分片示例。

## 快速开始

需要 Docker Engine/Compose，以及一个可引导 Wrapper 的兼容 Java（17 或更新版本）。项目编译、测试和运行任务使用 Gradle 自动获取的 Temurin 25；mise 不管理 Java 或 Gradle。

```bash
mise trust
mise install
mise run doctor
mise run db:up
./gradlew check
```

Git 通过 mise bootstrap 使用宿主包管理器维护：`mise bootstrap packages status` 检查，确需安装时执行 `mise bootstrap packages apply`。其版本接受发行版维护版本；其他辅助工具固定在 mise.toml/mise.lock。Serena 可选，构建无需 MCP。

运行 CLI：

```bash
mise run cli -- --output=json orders create \
  --customer-id 00000000-0000-0000-0000-000000000001 --description 'first order'
mise run cli -- --output=json orders list \
  --customer-id 00000000-0000-0000-0000-000000000001
mise run dev
```

服务启动后：

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"description":"http order"}' \
  http://localhost:8080/api/v1/customers/00000000-0000-0000-0000-000000000001/orders
curl http://localhost:9000/q/health/ready
```

API 的读取/完成始终需要 customerId 与 id。当前示例未实现认证；对外部署前接入身份与客户权限校验。`/web/demo` 是最小页面，`/web/redirect` 固定跳转；复杂前端按需另建 Node 工程。

## 分片示例与检查

```bash
mise run sharding:up
python3 scripts/dev.py cli --mode sharding -- --output=json orders list \
  --customer-id 00000000-0000-0000-0000-000000000001
./gradlew shardingCheck
./gradlew packageCheck
```

| 入口 | 验证范围 |
|---|---|
| `./gradlew check` | 格式、编译器静态检查、领域边界、业务、普通 PostgreSQL、Python 操作边界 |
| `./gradlew shardingCheck` | 真实 Citus 节点路由、仓储、CLI、重启和两种恢复 |
| `./gradlew packageCheck` | Java 25 启动真实 Jar，CLI/API 互通、离线帮助、错误 JSON、健康故障和退出 |
| `./gradlew assembleRelease` | 两种产物、CycloneDX SBOM、源码标识与 SHA-256 校验，保存在 build/release |

Docker 不可用会导致数据库测试失败，不会静默跳过。多个本地 Gradle 命令请串行执行，避免写同一构建目录；CI 作业使用独立检出。

## 产物与数据库配置

```bash
./gradlew :cli:quarkusBuild :server:quarkusBuild
java -jar cli/build/starter-cli-runner.jar --help
# 先在受控 shell 中加载本地运行配置：
set -a
. .local/db-single.env
set +a
java -jar server/build/quarkus-app/quarkus-run.jar
```

服务分发必须携带整个 server/build/quarkus-app 目录；CLI 分发单个 uber-jar。直接执行 Java 命令需要 Java 25 或更新版本，`mise run cli/dev` 使用 Gradle 的 Java 25。运行应用只需 APP_DB_JDBC_URL、APP_DB_USERNAME、APP_DB_PASSWORD；不要给生产服务迁移或管理凭据。上面的 source 方式仅用于本地演示。

迁移独立于 Quarkus 启动：`./gradlew :core:migrate`，从 APP_DB_MODE=single|sharding、APP_DB_JDBC_URL、APP_DB_MIGRATION_USERNAME、APP_DB_MIGRATION_PASSWORD 取值。正常应用只校验 schema。CLI 成功/输入/缺失/冲突/内部错误退出码分别为 0/2/3/4/1；`--output=json` 标准输出保持一个 JSON 值。

`mise run db:down`、`mise run sharding:down` 保留数据卷；reset 必须显式加 `--confirm-local-reset`。本地凭据、状态和备份在受忽略的 .local 中，保留状态文件才能安全管理已有环境。

## 维护资料

- [数据库与迁移](docs/database.md)、[分片限制](docs/sharding.md)、[API](docs/api.md)
- [操作与恢复](docs/operations.md)、[云部署](docs/cloud-deployment.md)、[数据库路线与成本](docs/database-options.md)
- [升级与回退](docs/upgrading.md)、[扩展与 Serena](docs/extensions.md)、[实际兼容记录](docs/compatibility.md)、[验证与审阅记录](docs/verification.md)
- [智能体约定](AGENTS.md)、[已批准设计](docs/superpowers/specs/2026-10-29-java-quarkus-starter-design.md)

发布工作流只归档验证后的产物；当前未执行远端发布、真实云部署或生产 HA 验证。许可证待项目维护者确定。
