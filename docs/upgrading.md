# 升级、依赖与回退

Quarkus BOM/Gradle 插件共用 gradle/libs.versions.toml 的 quarkus 版本。保持当前 LTS 系列，补丁更新先跑验证；切换下一条 LTS 作为显式升级决定。Dependabot 每周检查，暂不自动提出 Quarkus 次版本/主版本切换。

升级依赖时更新版本目录或对应构建脚本中的声明，审阅实际解析版本，再串行执行 `./gradlew check shardingCheck packageCheck --refresh-dependencies`，用干净 CI 验证。项目不维护 Gradle 依赖锁或校验元数据。Wrapper 用 `./gradlew wrapper --gradle-version <version> --gradle-distribution-sha256-sum <官方校验值>` 更新；保留分发包 SHA-256 校验，生成 Daemon JVM criteria 时指定 Java 25/ADOPTIUM。项目不依赖 mise 的 Gradle/JDK。

发布应用版本修改 gradle.properties 的 version，同时更新 RootCommand 的 CLI 展示版本，再执行 check、shardingCheck、packageCheck、assembleRelease。打 tag 前核对 tag、SOURCE.json 和产物版本一致。标签或手动工作流归档产物；不会自动创建云资源或部署到生产。许可证目前待定，不要把上游组件的许可证当作本仓库许可证。

build/release 包含 CLI 单 Jar、server 完整目录归档、两个 CycloneDX SBOM、SOURCE.json、SHA256SUMS。下载后用 `sha256sum -c SHA256SUMS` 验证；SBOM 来自 Quarkus 构建结果，当前 Gradle 生成器可能缺少部分许可证字段，发布前另行核查。无 Git 的本地工作区使用 sourceTreeSha256 标识源文件集合，gitCommit 为 null；CI 记录真实 GITHUB_SHA。

数据库变更按 expand/contract 演进：先增加兼容字段并迁移，旧应用仍可工作，再部署新应用，最后单独删除旧结构。回退应用镜像要求 schema 对旧版本仍兼容；Flyway 历史不直接改写或自动降级。破坏性迁移必须事先演练新库恢复与回切，不能只回退 Jar。

升级 PostgreSQL/Citus 时同时确认扩展、镜像内 PostgreSQL、客户端备份工具和权限。复跑真实物理路由、并发、生命周期、恢复和产物测试。示例版本固定在 Compose 与测试夹具，两处摘要需同步更新。JRE 镜像同样按摘要固定，改变后构建并执行非 root、CLI 和服务退出冒烟。

工具升级修改 mise.toml 中的精确版本，核验工具官方来源及命令。项目关闭 mise 锁文件。Git 使用发行版维护版本；Serena 只固定包版本，传递依赖完整锁定尚未解决。构建与测试不需要启动 Serena。

构建结构和官方参考见 [Gradle 约定](gradle.md)。版本目录由主构建和 included build 共享；升级时同时解析和验证 buildLogicCheck。Wrapper 重新生成脚本后确认 gradlew.bat 仍为 LF。
