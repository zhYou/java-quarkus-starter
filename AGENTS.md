# 项目约定

所有构建和项目 JDK 操作使用 ./gradlew。Gradle Daemon/Toolchain 使用 Temurin 25；mise 仅管理辅助工具，不添加 Java/Gradle。串行运行同一工作区的 Gradle 命令。不要修改全局 MCP、Java 或 mise 配置。

core/domain 保持纯 Java，application 管用例和事务，persistence 管 JPA，maintenance 管独立迁移和样例恢复。cli 与 server 只依赖 core，彼此独立。新业务规则集中到 core，不在入口复制实现。

修改行为先补充能暴露问题的测试，再实现并运行相关入口。`./gradlew check` 是普通检查；真实多节点用 shardingCheck；真实产物用 packageCheck。格式用 spotlessApply，静态检查采用 javac -Xlint:all -Werror 与 ArchUnit 领域边界。普通测试排除 sharding/recovery/packaged 标签，不跳过 Docker 故障。不要增加只镜像实现的测试或任意覆盖率门槛。

数据库连接使用 APP_DB_*。运行角色与迁移角色分离；应用不启动自动迁移。迁移只新增版本 SQL，不修改已发布迁移；改变分布键/分片拓扑必须先设计演练。同一客户是事务范围，组合主键和条件更新必须保留。

CLI offline preflight 不启动 Quarkus；业务执行启用 CDI 请求上下文。JSON stdout 只输出结果，诊断写 stderr，不泄漏 SQL、堆栈或凭据。HTTP 错误使用 Problem Details 和服务端 requestId。当前 customerId 不代表授权，接入真实业务须补 OIDC 与权限校验。

本地状态和凭据放 .local（目录700、文件600），禁止提交或打印。Docker 操作必须通过项目记录的 Compose 归属；down 保留卷，reset 要显式确认，禁止全局 prune。恢复总是新建目标，不自动替换源连接。Citus JSON 只用于本模板停写后的订单示例，生产使用专门备份方案。

辅助工具依 mise.toml/mise.lock，Git 使用 mise bootstrap 的宿主软件包。Serena 按需 `mise run serena`，子进程复用 Gradle JDK；构建和 CI 不依赖 MCP。Serena 的 Python 传递依赖当前未完整锁定。

Quarkus BOM 与插件共用 gradle/libs.versions.toml 版本，保持 LTS 主线。依赖变更更新锁和 SHA-256 元数据，并重新跑相关真实测试。产物、CI、部署和文档中的命令要能复现。清单只暴露业务 Service，管理端口仅内部探针/指标。发布、推送、云资源操作按用户授权执行。

设计和计划位于 docs/superpowers。提交前检查差异、忽略规则和相关验证结果；凭据、本地状态、缓存和构建产物不得提交。推送或远端发布须有用户授权。
