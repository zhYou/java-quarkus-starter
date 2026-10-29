# Java Quarkus 模板第一版设计

日期：2026-10-29。状态：用户已于 2026-10-29 确认。

本模板为智能体提供可运行、可测试、可持续维护的 Java Quarkus 工程起点，覆盖 CLI、HTTP API、少量 Web 响应和微服务部署。第一版采用 Gradle 多模块、普通 PostgreSQL，以及按需启动的 Citus 分片示例。

“用户已确认”记录会话中的明确选择。后续标为“首版提案”的内容是设计者补足的工程决定，已随本文通过用户审批。实施计划据此细化任务和验收命令。

## 用户已确认

- 项目公开托管在 GitHub，作为 Java Quarkus 模板使用。
- Java 和 Quarkus 优先采用最新 LTS；构建工具使用 Gradle，运行入口统一为 Gradle Wrapper。
- 项目 JDK 由 Gradle 管理，mise 管理 jq、git、ast-grep、Serena 等智能体辅助工具。
- 模板服务于 CLI、API、Web 和微服务开发，由智能体开发和维护。
- Web 需求较少，主要是简单页面或跳转；完整前端交给 Node 生态。
- 数据库访问属于第一版能力，配套事务、迁移和真实数据库集成测试。
- 默认数据库采用 PostgreSQL，第一版包含可运行的 Citus 跨节点分片示例。
- 成本策略为生产优先托管 PostgreSQL，开发、测试和分片示例按需运行。
- 项目采用多模块组织，CLI 与 HTTP 服务共享业务代码。

## 目标与验收结果

使用者取得模板后，可以通过文档中的命令安装辅助工具、检查环境、启动普通数据库、运行 CLI 或 HTTP 服务，并执行与 CI 一致的检查。

第一版完成时：

1. CLI 和 HTTP 服务可以分别构建和启动，调用同一套业务与数据访问实现。
2. 同一套订单示例在普通 PostgreSQL 和 Citus 上通过行为测试。
3. 分片测试证明数据实际分布到两个工作节点，并验证按分布键查询与更新正确。
4. 数据库迁移能够作用于空库和已有数据；生产启动使用运行账户，迁移使用独立凭据。
5. 格式、静态检查、测试、产物验证在 GitHub Actions 中自动执行。
6. 日志、健康检查、指标和错误信息支持智能体定位常见故障。
7. 数据、工具和应用版本有明确的固定方式和升级步骤。

第一版主要验证 Linux 开发与 CI 环境；macOS 提供相同容器工作流说明，Windows 提供 WSL2 使用说明。后两者的实际验证结果须如实记录。

## 方案比较

| 决策 | 考虑过的方案 | 选择及理由 |
|---|---|---|
| 工程组织 | 单模块承载全部入口；共享模块加独立 CLI 和 HTTP 应用 | 用户选择多模块。入口可以独立运行，业务修改通过共享测试验证 |
| 分片实现 | PostgreSQL 加 ShardingSphere；PostgreSQL 加 Citus；YugabyteDB 或 TiDB 等分布式引擎 | 用户选择 PostgreSQL 加 Citus。保留 PostgreSQL 基础，并提供数据库端跨节点分片示例 |
| 生产数据库 | 自建普通 PostgreSQL；托管 PostgreSQL；直接运行分布式集群 | 用户认可生产优先托管 PostgreSQL，分片示例按需启动，具体项目按负载决定生产拓扑 |
| MySQL 路线 | 普通 MySQL；Vitess 或 ShardingSphere；TiDB 和云分布式产品 | 已比较其能力、成本和维护要求，首版实现集中于 PostgreSQL 和 Citus |

## 首版提案与边界

第一版提供一个 HTTP 微服务起点和一个独立 CLI 应用。微服务能力落实为外部配置、OCI 镜像、健康与就绪检查、优雅退出和可观测性接入。

本次不接入真实支付、身份提供方、Redis、消息队列或云厂商 SDK；这些能力通过扩展说明描述接入位置。订单是数据访问示例，不实现交易系统。业务鉴权与租户隔离留给衍生项目，示例中的 customerId 仅用于业务查询和分片路由。

本次交付 JVM 产物和 OCI 构建方式。Native Image、跨云多活、自动分片扩缩、生产 Citus 高可用集群和生产基础设施创建属于后续范围。

完整 Node 前端、MySQL 可运行配置及其他数据库集群不纳入首版实现。

## 模块与依赖

| 模块 | 职责 | 产物 |
|---|---|---|
| core | 领域模型、业务用例、数据访问接口与 PostgreSQL 实现、迁移资源、维护任务入口 | 普通 Java 库 |
| cli | Picocli 命令、参数校验、输出和退出码转换 | 独立 Quarkus 命令行程序 |
| server | Quarkus REST 接口、HTTP 错误转换、轻量 Web 响应、管理端点 | 独立 Quarkus HTTP 服务 |

cli 和 server 依赖 core，二者相互独立。core 内使用 domain、application、persistence 包区分职责；domain 保持为普通 Java，业务用例通过接口访问持久层，HTTP DTO 和 CLI 参数留在各自入口模块。

core 中的 CDI Bean 与持久化模型需要被两个 Quarkus 应用正确发现。采用 Quarkus 官方支持的依赖发现方式，并通过打包后测试验证；不依靠仅在开发模式下可用的扫描行为。

顶层放置 Gradle 构建约定、mise 配置、AGENTS.md、容器环境、CI 和维护文档。默认项目标识拟为 java-quarkus-starter，Java 包名拟为 io.github.zhyou.starter。README 列出使用模板后需要替换的标识。

## 构建与工具

| 项目 | 首版基线或规则 |
|---|---|
| Java | 25 LTS；Temurin 为默认发行版提案 |
| Quarkus | 3.40 LTS 系列，当前候选补丁 3.40.1.1；平台 BOM 与 Gradle 插件同步 |
| Gradle | Wrapper 固定兼容稳定版；优先验证 9.8.2，若有兼容问题，使用官方 3.40 指南推荐的 9.7.0 并记录原因 |
| 构建脚本 | Kotlin DSL；应用代码保持 Java |
| PostgreSQL | 18 系列，镜像补丁和摘要在兼容验证后固定 |
| Citus | 支持 PostgreSQL 18 的 14 系列；镜像和 PostgreSQL 版本成套验证、固定 |
| 其他 Java 依赖 | 优先使用 Quarkus BOM 支持版本，额外依赖明确固定 |

Gradle Toolchains 管理编译和测试用 JDK，并配置自动下载来源。Gradle Daemon JVM 条件与项目 JDK 对齐。首次启动 Wrapper 仍需要环境中已有兼容的 Java；这属于引导前提，mise 不安装或切换 Java。

mise 管理 jq、git、ripgrep、ast-grep，以及启动 Serena 所需的 Python/uv 和 Serena 版本。Serena 若没有合适的发布包，使用固定提交来源。工具版本不得使用滚动的 latest 或浮动 Git 分支。

mise 本身、启动 Java 和容器引擎属于环境引导前提；初次获取仓库所需的 Git 与后续由 mise 选择的 Git 版本分别说明。Docker Engine 的安装由宿主环境负责，项目提供检查方法。

Serena 按需启动，Java 构建和测试无需启动 MCP 服务。项目提供按当前工作目录启动的说明，不修改用户全局智能体配置。Serena Java 语言服务若需要额外 JDK，应优先复用 Gradle 管理的 JDK，并明确配置步骤。

mise 可提供任务别名，但 Java 构建的规范入口始终是 ./gradlew。固定 Wrapper 下载校验值，管理依赖锁定与校验元数据。

## 共享业务示例

使用订单记录演示创建、按客户查询、按主键查询以及状态更新。最小字段为：

| 字段 | 含义 |
|---|---|
| customer_id | UUID，业务查询键和 Citus 分布键 |
| id | UUID，由应用生成 |
| description | 有长度限制的示例文本 |
| status | 有限状态值，首版只允许 NEW 到 COMPLETED |
| created_at | 以 UTC 语义存储和输出的创建时间 |

主键采用 customer_id 与 id 的组合，以满足分布表的唯一性约束。对外查询和更新始终携带二者；UUID 的生成不构成跨分片全局唯一约束的承诺。

创建和更新用例具有清晰的事务边界。状态更新必须处理并发竞争，例如使用条件更新并核验影响行数。首版事务限于同一 customer_id；跨客户事务与跨分片复杂 JOIN 不属于示例契约。

持久化首版提案采用 Hibernate ORM 与仓储实现，使用 PostgreSQL JDBC 和 Agroal。按客户的集合查询有稳定排序和数量上限，首版不提供无限制全库列表接口。

## 普通数据库与 Citus

普通模式启动一个 PostgreSQL 实例。分片模式启动一个 Citus 协调节点和两个 Citus 工作节点，通过相同的 PostgreSQL 驱动连接协调节点。该本地拓扑用于演示分片，不承担生产高可用验收。

两个模式的数据卷、连接地址和生命周期分别管理，默认只启动普通模式。示例数据和真实生产凭据使用不同配置，维护任务限制在项目拥有的本地环境中执行。

两种模式共用基础表结构。迁移位置区分公共 schema 迁移与 Citus 专用迁移；后者建立扩展、注册节点并将订单表转换为分布表。扩展和节点初始化由数据库管理阶段完成，业务运行账户只承担查询和数据修改。

客户路由采用 Citus 哈希分布，示例预设有限分片数。测试使用固定数据集，读取分片元数据并核验工作节点上的实际记录，证明至少两个节点存储了不同的数据分片。业务侧始终访问一个逻辑订单表。

节点注册、分布表创建和初始化必须可重复执行。重新运行启动任务保留已有数据；清空并重建使用单独的显式重置任务，并仅作用于项目的本地示例资源。

## 迁移、导出与恢复

公共与 Citus 专用 schema 迁移均由 Flyway 执行。开发和测试任务自动串联初始化与迁移。生产应用默认不在启动时修改 schema，提供独立 Gradle 维护任务，使用迁移账户运行 Flyway API。

迁移任务无需启动 CLI 或 HTTP Quarkus 应用，避免 ORM 校验先于空库迁移发生。正常应用启动检查 schema；不匹配时给出明确错误。生产运行账户和迁移账户由不同环境变量提供，日志隐藏凭据。

普通 PostgreSQL 提供逻辑备份、恢复到新库和结果校验的演练。

Citus 提供停写条件下的示例数据导出、同版本拓扑重建、迁移和数据导入演练，恢复范围为模板管理的示例 schema 与数据，随后验证业务结果及分片分布。生产集群的一致性备份、持续归档和高可用恢复交由具体部署方案定义。

## CLI 与 HTTP 契约

CLI 提供 help、version 和 orders 命令组。默认文本输出，显式选项切换 JSON。成功结果写标准输出，诊断和日志写标准错误；JSON 输出保持可解析。

| CLI 退出码 | 语义 |
|---|---|
| 0 | 成功 |
| 2 | 参数或输入无效 |
| 3 | 记录不存在 |
| 4 | 状态冲突 |
| 1 | 数据库不可用或未分类内部错误 |

HTTP API 在 /api/v1 下提供创建、按客户列表、按组合主键读取和状态更新接口。成功使用与操作相符的状态码和 DTO；输入错误、记录缺失、状态冲突分别映射 400、404、409。错误响应采用 RFC 9457 Problem Details，并包含稳定业务错误码和请求关联标识。

API 提供 OpenAPI 文档，Swagger UI 仅在开发和测试环境默认启用。首版 Web 示例只演示最小 HTML 响应以及到固定本地目标的跳转，不接收任意外部跳转地址。

CLI 与 HTTP 入口处理输入和输出转换，业务校验与事务行为由 core 实现。首版示例 API 不含用户认证，文档明确接入 OIDC 和权限校验的位置；生产身份模型由衍生项目确定。

## 配置、可观测性与部署

开发、测试和生产配置分开，必需配置在启动或维护任务运行前验证。普通和分片模式通过连接地址及初始化配置选择，业务代码保持一致。环境变量示例使用无效占位凭据，实际本地配置和导出文件加入忽略列表。

开发日志便于阅读，生产日志采用 JSON，并包含请求关联信息。CLI 日志遵守标准错误约定。异常响应隐藏堆栈、SQL 细节和凭据，日志保留可排障的错误分类。

server 提供存活、就绪检查与指标；就绪状态体现数据库访问能力。管理端点与业务端点分开配置，Kubernetes 示例通过内部端口访问探针。OpenTelemetry 提供可选导出配置，默认运行无需外部采集服务。CLI 执行完任务后退出，不启动 HTTP 管理服务。

首版交付两个应用的 JVM 运行产物及 OCI 构建方式。server 另外提供通用 Kubernetes Deployment、Service、资源配置和探针示例，使用已有 Secret 引用。CLI 不部署为常驻服务，文档说明作为一次性容器任务运行。

AWS、阿里云、腾讯云文档说明如何连接已有托管 PostgreSQL，包括 TLS、连接池预算、凭据配置、备份恢复和版本核验。首版不创建云资源，不部署跨云集群。托管产品的 Citus 支持须按实际服务核实，不能将本地 Citus 配置直接视为三云托管配置。

## 智能体与工程检查

AGENTS.md 说明模块职责、规范命令、变更验证要求、生成文件边界和文档位置。项目指令优先使用现有可验证命令，避免将关键约定只保存在某个智能体的私有记忆中。

首版采用统一自动格式化和静态检查，补充有实际作用的包依赖边界检查。实现者选择支持 Java 25 的工具版本，并控制重复规则。测试关注行为、数据迁移和分片结果，不使用任意覆盖率数字代替验收。

预期任务入口：

| 任务 | 契约 |
|---|---|
| doctor | 检查启动 Java、mise 工具和容器环境，给出修复路径 |
| db:up / db:down | 启停普通数据库，保留数据 |
| sharding:up / sharding:down | 启停 Citus 示例，保留数据 |
| db:reset / sharding:reset | 显式重置本地示例资源 |
| migrate | 使用指定模式与迁移账户执行 Flyway |
| dev | 启动 HTTP 开发模式 |
| cli | 运行命令行示例并传递参数 |
| check | 格式验证、静态检查、业务和普通数据库测试 |
| sharding:check | 完整 Citus 行为、路由和恢复演练 |
| package:check | 验证两个打包产物可以独立运行 |

这些名称是任务契约，具体 Gradle 和 mise 命令映射由实施计划固定。AGENTS.md 和 README 必须使用最终可运行命令，不能留下占位命令。

GitHub Actions 的 PR 检查分别运行常规检查、Citus 检查和产物测试，失败上传必要报告并清理容器。构建缓存避免重复下载，不缓存凭据或示例数据。CI 不依赖付费数据库、云凭据或 Serena 服务。

提供依赖与镜像升级配置、Wrapper 校验和依赖审查流程；Quarkus BOM 与插件保持同步，LTS 主线升级通过独立变更处理。构建产物记录版本、源码标识和依赖清单。发布工作流通过手动或标签触发，具体远端发布在建立有效仓库后处理。

## 必需测试

| 范围 | 必须证明的行为 |
|---|---|
| core | 输入和状态规则、错误分类、并发更新结果 |
| 普通 PostgreSQL | 真实读写、事务回滚、组合主键、稳定列表、数据保留 |
| 迁移 | 空库建表、重复执行、从前一 schema 带数据升级 |
| CLI | 帮助、退出码、JSON 标准输出、诊断标准错误、数据库失败 |
| HTTP | 成功与错误响应、输入校验、请求关联、固定目标跳转 |
| Citus | 扩展与拓扑初始化、跨工作节点分布、路由、同分片回滚、重启保留数据 |
| 导出恢复 | 恢复到干净环境后业务数据一致，Citus 分片分布正确 |
| 产物 | CLI 与 server 分别启动，core Bean 可发现，常规与分片配置均可连接 |
| 部署说明 | OCI 构建可执行，Kubernetes 清单完成静态校验，托管接入文档无实际密钥 |

Citus 测试采用真实多节点环境，不能仅依靠 SQL 日志断言。启动等待有超时和诊断，固定测试数据避免依赖随机分布。测试数据与数据库实例在 CI 作业之间隔离。

## 文档与发布边界

交付 README、AGENTS.md、数据库与分片说明、维护与恢复说明、云部署说明、版本升级约定，以及本设计和后续实施计划。MySQL 等备选方案以选型比较说明保留。

开源许可证尚未选择，本次设计与实现不代替用户作出许可授权；公开发布前单独确定 LICENSE。GitHub 模板仓库设置、远端提交和发布不由本次文档审批自动触发。

当前工作区没有可读取的有效 Git 仓库元数据。设计先保存在工作区，Git 元数据可用后再提交；不能将本地文档保存报告为已经提交或公开发布。

## 留给实现者的决定

- 在上述版本主线内选择经过验证的补丁、插件版本和镜像摘要。
- 固定每个任务的实际命令、参数传递方式、端口和本地资源名称。
- 选择满足约定的 CDI 发现方式、仓储细节、并发更新机制和错误转换实现。
- 选择 Java 25 兼容的格式化、静态分析与测试工具，保持检查职责清晰。
- 选择 Citus 示例数据导出和恢复的具体实现，满足停写、同版本、完整校验的约束。
- 在测试和文档中记录真实验证结果；兼容性问题必须修复或明确提出设计变更，不静默切换数据库或退出 LTS 主线。

## 核验依据

版本与产品能力依据截至 2026-10-29 的官方资料；最终补丁与镜像可用性在实施时再次核验。

- Java LTS：[Oracle 支持路线图](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)。
- Quarkus LTS：[发布记录](https://quarkus.io/releases/)；Gradle 与多模块：[官方指南](https://quarkus.io/guides/gradle-tooling/)。
- JDK 管理：[Gradle Toolchains](https://docs.gradle.org/current/userguide/toolchains.html)、[Daemon JVM 条件](https://docs.gradle.org/current/userguide/gradle_daemon.html)。
- Citus 与 PostgreSQL 18：[Citus 14 发布资料](https://www.citusdata.com/blog/2026/)；[分布表约束](https://docs.citusdata.com/en/stable/develop/reference_ddl.html)；[集群管理](https://docs.citusdata.com/en/stable/admin_guide/cluster_management.html)。
- Quarkus：[数据源](https://quarkus.io/guides/datasource/)、[Flyway](https://quarkus.io/guides/flyway/)、[测试](https://quarkus.io/guides/getting-started-testing/)、[可观测性](https://quarkus.io/guides/observability/)。
- 工具：[mise registry](https://mise.jdx.dev/registry)、[Serena 启动方式](https://oraios.github.io/serena/02-usage/020_running.html)。
- HTTP 错误：[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)。
