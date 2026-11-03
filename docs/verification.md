# 实施与验证记录

验证日期：2026-10-29。已批准的 8 项实施任务均已落地。实施与独立审阅阶段没有可用 Git 元数据；随后用户单独授权提交和推送，发布阶段初始化了本地仓库。

## 实际结果

最终命令：`./gradlew spotlessApply check shardingCheck packageCheck assembleRelease --offline`，结果 BUILD SUCCESSFUL，4 分 5 秒。该次运行使用严格依赖校验，没有写锁或校验元数据以绕过验证。

| 套件 | 通过数量 | 失败/跳过 |
|---|---:|---:|
| core 普通测试 | 9 | 0 |
| CLI 普通测试 | 3 | 0 |
| server 普通测试 | 12 | 0 |
| server 真实分片测试 | 6 | 0 |
| CLI 分片契约 | 1 | 0 |
| 两种数据库恢复测试 | 2 | 0 |
| 真实 Jar 进程测试 | 3 | 0 |
| Python 操作边界测试 | 10 | 0 |

Java 共 36 项，另有 Python 10 项。真实进程套件覆盖两个数据库模式的 CLI 创建→HTTP 读取/完成→CLI 查询、离线帮助、无效输入、数据库失败 JSON、readiness/liveness 和 SIGTERM。

实际 Compose 操作额外验证普通库及 Citus 的 up、CLI 写入、down/up 保留、备份/导出、新目标恢复和源数据保留。Citus 修复了重复拓扑写入与临时初始化服务健康检查竞态，均保留了失败复现与后续通过证据。测试拥有的恢复目标已清理；两个源示例卷保留并停止，没有操作其他 Docker 项目。

两个 OCI 镜像已在审阅修复后重新构建；再次验证了 CLI 无网帮助、非 root HTTP 运行与 SIGTERM。actionlint 校验 CI/发布工作流通过；kubeconform 严格校验 2 个 Kubernetes 对象通过；doctor 与辅助工具版本核验通过。Serena 命令帮助可用，子进程复用 Gradle JDK 的行为由边界测试验证。

build/release 中 5 个文件的 SHA-256 已核验，server 归档携带完整 quarkus-app/lib。CLI/server 的 CycloneDX 清单分别含 269/439 个组件。源码标识在 SOURCE.json；首次验收归档的 gitCommit 为 null，以 sourceTreeSha256 标识源集合；仓库初始化后的归档会记录实际 HEAD。

## 执行取舍

1. 实施期间 Git 元数据不可用：按已批准计划在原工作区实施，保留逐任务验证账本。随后按用户提交、推送的授权初始化仓库；代价是历史实施过程没有逐步提交，首个提交保存完整验收快照。
2. Git 通过 mise bootstrap 管理发行版软件包：没有项目 registry 项，源码插件还依赖额外编译工具及受限 API；本次没有变更宿主软件包。代价是不同宿主的 Git 版本可能不同，doctor 只检查可用性。
3. 格式化固定 Java 25 Daemon、Spotless 8.10.4 与 google-java-format 1.36.1：新 formatter 1.37 改变接口。代价是升级格式工具需配套兼容验证；当前检查没有压制 lint。
4. HTTP 错误与并发断言合并在同一测试类，负向健康行为放到真实产物进程测试。代价是必须保留 packageCheck 才有完整进程故障验收，本次已运行通过。

逐项执行证据与审阅工作区位于本地忽略目录 `.superpowers/sdd/2026-10-29-java-quarkus-starter/`。本地仍保留该目录作为实施阶段的审计记录，目录本身不提交。

## 已知验证范围

上述本地验收不包含真实云部署、GitHub 云端流水线或远端版本发布；公开仓库推送后的 CI 结果以 GitHub Actions 为准。当前示例 API 没有身份认证，Citus 没有生产 HA/增量恢复，复杂前端和真实支付未实现，均属于已批准的首版边界。

Serena 的 Python 传递依赖未完整锁定，Java LSP 交互未完整验收。上游 Gradle SBOM 生成器可能遗漏部分许可证字段；OSGi about.html 重复、测试 OpenTelemetry API usage 与指标重复注册提示已记录，没有通过隐藏警告或跳过测试规避。

## 独立审阅与修复

由独立 gpt-6-astra 审阅整个项目，只读检查，无第二轮审阅。未发现 Critical；3 项 Important 均已修复。HTTP 405 的协议头问题重新定级为 Important，并在同一修复批次处理。

| 问题 | 回归与结果 |
|---|---|
| 父 shell 导出的源配置覆盖 Compose 恢复目标 | RestoreEnvironmentTest 先失败后通过；所有 Compose 子进程显式注入目标配置；真实源环境已导出时的新目标恢复通过 |
| HTTP 对空白规范化前限制 200 字符，行为与 CLI/core 不同 | validatesDescriptionLengthAfterNormalization 先失败后通过，规范化后 200/201 边界统一由 core 判断 |
| JSON 拼接根对象或垃圾尾部被忽略 | rejectsConcatenatedRootsAndGarbageBeforeUsingConnection 先失败后通过，启用 FAIL_ON_TRAILING_TOKENS |
| 405 丢失 Allow 且 405/415 标题错误 | methodAndMediaErrorsPreserveHttpSemantics 先失败后通过；检查列表、单订单、完成与 Web 路由，按实际路由生成 Allow |

上述修复后完整套件 36 项 Java、10 项 Python 均通过，无跳过；严格校验的离线构建通过。当前 Quarkus 上游产生的 405 本身缺少 Allow，代码使用其公开路由元数据补齐，升级时需保留对应回归。

保留两项次要改进：共享仓储契约尚未固定同 id 不同客户的更新隔离断言（当前实现双键条件正确）；doctor 尚未检查 Wrapper 启动 Java（README 已明确要求先有兼容 Java）。它们未作为本轮重要修复处理。

审阅中未展开的行为均按已批准范围或已有验证边界保留：认证/OIDC 与真实客户权限；真实支付、缓存/MQ、完整前端、Native Image、生产 Citus HA/扩容及生产备份；远端 GitHub/云执行和许可证选择；实施阶段的 Git 提交集成（已在后续发布授权中处理）；Serena 完整 LSP/传递依赖锁；已公开的上游 SBOM/重复资源/遥测提示；其他平台兼容性。代价分别是采用这些能力时仍需实现/验收、部署时仍需远端与许可核验、Git 恢复后仍需集成、工具供应链与其他平台仍需补测。没有将这些项目宣称为已完成或已验证。

## 首次公开推送后的冷缓存校验

首次 GitHub CI 在插件 classpath 解析阶段暴露 16 个缺失的校验条目：4 个父 POM、12 个 JUnit BOM module 文件。它们不是现有摘要不匹配，而是热缓存生成时没有被完整记录。修复时刷新完整依赖解析，并从 Maven Central 独立下载、核对新增摘要；严格依赖校验保持开启。后续依赖升级沿用冷缓存验证，避免只依赖本机历史缓存。

最终补齐 25 个元数据文件的 SHA-256，全部与 Maven Central 独立下载结果一致，原有摘要与校验策略未变。刷新依赖后 check/shardingCheck/packageCheck 通过；使用独立 Gradle 用户目录、关闭配置缓存且无共享依赖缓存的 help 解析通过（仅复用 Wrapper 分发包及已有引导 JDK），覆盖首次 CI 失败的插件 classpath 路径。

## 2026-11-02：Gradle 构建逻辑与开发工具改造

构建入口已转换为 Groovy DSL，公共约定位于 build-logic，原项目 Python 工具和测试由 Java/Gradle 接手。CLI 业务命令与直接 Jar 的退出码保持不变；Gradle 便捷入口采用 Gradle 失败语义。

完整组合命令：

```bash
./gradlew check shardingCheck packageCheck assembleRelease \
  --rerun-tasks --no-build-cache --offline --console=plain
```

结果 BUILD SUCCESSFUL，3 分 12 秒，70 个任务全部执行。构建逻辑包含 16 个 Java/TestKit 测试，覆盖本地状态与权限、资源选择、恢复隔离、发布摘要、真实编译告警、配置缓存和 Serena 进程边界。

额外验证：

- 使用全新独立 Gradle 用户目录，只复用已验证的 Wrapper 分发包和 Gradle 管理的 JDK，执行 help/buildLogicCheck；严格依赖校验下 24 个任务全部执行成功。TestKit 跟随调用者的 offline 设置，夹具使用根 SHA-256 校验策略。
- 新增的 11 个依赖摘要分别从 Maven Central 独立下载核对；没有修改或删除原有摘要。
- 连续两次执行 `:core:clean :core:compileJava`，第二次复用配置缓存且 compileJava 为 FROM-CACHE；连续查询 `:core:javaPath` 返回相同的受管启动器。
- 使用旧 Python Controller 格式创建隔离状态，真实执行 dbUp、CLI 写入/查询、私有 pg_dump、导出源 APP_DB_* 后恢复到新目标、目标数据对比、源凭据稳定性、down/up 保留数据；临时目标仅通过所属 Compose 显式重置并清理。
- 本地 dev 连接所选数据库并通过 readiness 检查，关闭自动 DevServices 后不启动额外的可观测性容器；测试进程和临时数据库均已清理。
- Serena 使用私有可执行文件验证 stdin/stdout JSON 往返，stdout 精确匹配且 stderr 为空；TestKit 另验证 Java 25、带空格参数、重复缓存调用和无需数据库状态。未宣称完成真实 Serena Java LSP 全流程验收。
- 独立计算现存 Git 管理源文件的摘要，与 SOURCE.json 一致；核对发布目录的 5 个 SHA-256 条目、完整 server 归档和两份非空 CycloneDX SBOM。
- actionlint、Kubernetes kubeconform、18 个 mise 任务配置、忽略规则和 LF 审计通过。

审阅和集成验证发现的问题均先复现再修复：状态文件必须与请求的 mode/name 一致；Quarkus 开发任务需要在输入定型前延迟加载环境，并单独退出配置缓存；同模块普通/分片 HTTP 测试必须按顺序运行以避免争用 8081。修复后的完整组合命令已通过。

原有上游 OpenTelemetry API usage、指标重复注册、依赖 about.html 重复和 SBOM 许可证字段提示仍保留，没有隐藏警告或跳过 Docker 测试。本次只记录 Mandrel 25 的原生构建方式，不新增或宣称验收 Native 发布流水线。生产备份仍不属于示例 JSON 恢复能力。

## 2026-11-03：构建配置与命令入口精简

convention 脚本及其 Java 辅助类、测试上移至 build-logic 根项目，operations 保留。三个约定 ID 改为 java-conventions、library-conventions、application-conventions。历史记录中的 conventions 子项目、Daemon JVM criteria 和 mise 任务别名安排由[本次设计](superpowers/specs/2026-11-03-simplify-gradle-design.md)取代。

先在隔离 worktree 运行 buildLogicCheck 确认基线，再只更新 TestKit 使用的插件 ID；三个用例均因新 ID 无法解析而失败。完成迁移后原用例通过。格式化和 buildLogicCheck 分别以 3 秒、9 秒执行成功。

环境通过命令级 JAVA_HOME 提供已有的 Temurin 25.0.5+8-LTS，没有修改全局配置。删除 Daemon JVM criteria 后，`./gradlew --version` 显示 Launcher 与 Daemon 使用同一 Java 25，`./gradlew -q :core:javaPath` 返回相同 JDK。源码编译仍显式使用 UTF-8，仅删除重复的 JVM 编码参数。

完整验证命令：

```bash
./gradlew check shardingCheck packageCheck assembleRelease \
  --rerun-tasks --no-build-cache --console=plain
```

结果 BUILD SUCCESSFUL，2 分 41 秒，70 个任务全部执行。构建逻辑 26 项、业务与真实进程 36 项测试全部通过，失败、错误和跳过均为 0，包含真实 Citus、两种恢复及 Jar 进程测试。

另外核对了 5 个发布文件的 SHA-256、server 归档中的运行时依赖和统一时间戳；CLI/server 的 CycloneDX 清单分别包含 269/439 个组件。mise TOML 可解析，全部 18 个任务包装已删除，辅助工具与 bootstrap 声明保留。现行使用文档已统一直接调用 Wrapper；`./gradlew doctor` 通过。

本次验证环境为 Linux/WSL2 x86_64，原生 Windows、Native Image 和真实 Serena Java LSP 全流程未验收。既有上游遥测、重复资源和 SBOM 许可证提示未隐藏。
