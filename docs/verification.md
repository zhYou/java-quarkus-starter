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

未执行真实云部署、GitHub 云端流水线或远端发布。当前示例 API 没有身份认证，Citus 没有生产 HA/增量恢复，复杂前端和真实支付未实现，均属于已批准的首版边界。

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
