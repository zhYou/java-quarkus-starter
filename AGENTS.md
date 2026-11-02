# 项目约定

所有构建和项目 JDK 操作使用 ./gradlew。Gradle Daemon/Toolchain 使用 Temurin 25；mise 仅管理辅助工具，不添加 Java/Gradle。串行运行同一工作区的 Gradle 命令。不要修改全局 MCP、Java 或 mise 配置。

core/domain 保持纯 Java，application 管用例和事务，persistence 管 JPA，maintenance 管独立迁移和样例恢复。cli 与 server 只依赖 core，彼此独立。新业务规则集中到 core，不在入口复制实现。

修改行为先补充能暴露问题的测试，再实现并运行相关入口。`./gradlew check` 是普通检查；真实多节点用 shardingCheck；真实产物用 packageCheck。格式用 spotlessApply，静态检查采用 javac -Xlint:all -Werror 与 ArchUnit 领域边界。普通测试排除 sharding/recovery/packaged 标签，不跳过 Docker 故障。不要增加只镜像实现的测试或任意覆盖率门槛。

数据库连接使用 APP_DB_*。运行角色与迁移角色分离；应用不启动自动迁移。迁移只新增版本 SQL，不修改已发布迁移；改变分布键/分片拓扑必须先设计演练。同一客户是事务范围，组合主键和条件更新必须保留。

CLI offline preflight 不启动 Quarkus；业务执行启用 CDI 请求上下文。JSON stdout 只输出结果，诊断写 stderr，不泄漏 SQL、堆栈或凭据。HTTP 错误使用 Problem Details 和服务端 requestId。当前 customerId 不代表授权，接入真实业务须补 OIDC 与权限校验。

本地状态和凭据放 .local（目录700、文件600），禁止提交或打印。Docker 操作必须通过项目记录的 Compose 归属；down 保留卷，reset 要显式确认，禁止全局 prune。恢复总是新建目标，不自动替换源连接。Citus JSON 只用于本模板停写后的订单示例，生产使用专门备份方案。

辅助工具依 mise.toml/mise.lock，Git 使用 mise bootstrap 的宿主软件包。Serena 按需 `mise run serena`，子进程复用 Gradle JDK；构建和 CI 不依赖 MCP。Serena 的 Python 传递依赖当前未完整锁定。

Quarkus BOM 与插件共用 gradle/libs.versions.toml 版本，保持 LTS 主线。依赖变更更新锁和 SHA-256 元数据，并重新跑相关真实测试。产物、CI、部署和文档中的命令要能复现。清单只暴露业务 Service，管理端口仅内部探针/指标。合并、发布和云资源操作须经用户明确授权。

设计和计划位于 docs/superpowers。提交前检查差异、忽略规则和相关验证结果；凭据、本地状态、缓存和构建产物不得提交。任务分支的提交、推送和 PR 按下文交付流程执行。

构建逻辑采用 build-logic included build：Groovy convention plugins 复用配置，Java 实现复杂任务；模块显式应用插件，不使用 allprojects/subprojects/afterEvaluate 注入公共配置。遵循 [Gradle 构建约定与官方资料](docs/gradle.md)；CLI 设计参考 [项目 CLI 约定](docs/cli.md) 与 https://clig.dev/，保留 Jar 的输出及退出码契约。

## 隔离开发与交付

默认在独立 worktree 中修改。同一任务已有隔离 worktree 时复用；否则优先使用工具原生 worktree 能力，手动创建时放在主工作区根目录的 `.worktrees/` 下。仓库内的 worktree 目录须被 Git 忽略。隔离失败时报告原因，不静默改在主工作区实施。

基线优先使用用户指定的引用；否则结合已有 PR 目标、任务上下文和 Git 分支关系确定。无法唯一确定时请用户指定，不直接回落到 `main`。开始修改前说明基线引用及提交、任务分支和 PR 目标分支。

新分支命名为 `<类型>/<YYMMDD>-<主题>`，手动 worktree 目录命名为 `.worktrees/<YYMMDD>-<类型>-<主题>/`。日期取首次创建时的 UTC 日期；类型使用 `feat`、`fix`、`docs`、`refactor`、`test`、`build`、`ci`、`perf`、`chore` 或 `revert`；主题使用小写英文、数字和连字符。例如分支 `docs/261102-isolated-delivery` 对应目录 `.worktrees/261102-docs-isolated-delivery/`。

创建前检查本地、远端分支、现有 worktree 和目标目录是否重名；冲突时在主题末尾追加 `-2`、`-3`，直到可用。同一任务续做沿用原名。工具托管且无法指定的目录名允许保留，交付分支仍遵守命名规则。

各 worktree 独立管理 `.local` 和本地 Compose 归属，不复制其他工作区的凭据或状态，也不接管其容器。Gradle、JDK、Docker 和重置确认继续遵守上文约定。

修改完成并通过与改动相关的必需检查后，提交、推送任务分支；已有本任务 PR 时更新同一 PR，否则按仓库 PR 模板创建指向已确定目标分支的 PR，不重复询问交付方式。PR 说明应反映最终改动、验证结果和必要限制。

创建 PR 后报告 PR 链接、验证结果、任务分支和 worktree 位置，保留工作区用于审查修复。智能体执行开发和维护，维护者负责需求、合并和发布决策，不自动合并 PR。

确认 PR 已合并且本地无未推送提交后，先检查未提交、未跟踪及被忽略文件，将需要保留的内容移到该 worktree 之外；敏感内容仍须保持目录 700、文件 600。清理前按项目记录的 Compose 归属停止本任务启动的服务，保留卷；删除数据或卷仍须显式确认。

清理时优先使用工具原生能力移除本任务 worktree，再依次删除本地任务分支及尚未自动删除的远端任务分支。仅清理本任务资源；遇到异常时报告原因，不强制删除。PR 关闭但未合并时保留分支和 worktree，待维护者明确放弃后，先完成上述文件检查和内容保留，再按同一顺序清理。

沿用现有 Git 作者身份，不修改个人 Git 配置。提交尾注如实记录执行工具，例如 `Agent: Codex`、`Agent: Claude Code` 或 `Agent: OpenCode`。
