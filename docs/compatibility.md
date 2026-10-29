# 兼容性记录

基线日期：2026-10-29。实际验证平台：Linux x86_64，Docker Engine 29.9.1。

| 组件 | 固定选择 | 实际验证 |
|---|---|---|
| Quarkus | 3.40.1.1 LTS，BOM/插件同源版本目录 | CLI、HTTP、真实 Jar 与两种 DB 模式 |
| Gradle | 9.8.2，Wrapper SHA-256 | 所有构建均经 Wrapper |
| 编译/测试 JDK | Temurin 25.0.5+8-LTS，Toolchain/Daemon 标准 Java 25 | Gradle 自动获取，javaPath 与测试进程 |
| OCI JRE | Temurin 25.0.4.1+1-LTS Alpine，摘要固定 | 镜像内 java -version、非 root CLI/HTTP 和 SIGTERM |
| PostgreSQL | postgres:18.6-alpine，摘要固定 | 仓储、迁移、权限、并发、pg_dump/pg_restore |
| Citus | citusdata/citus:14.2.0-alpine，内置 PostgreSQL 18.4，摘要固定 | 一协调两 worker、物理路由、重启、重复初始化、示例恢复 |
| JDBC/Flyway/Testcontainers | 42.7.13 / 12.0.0 / 2.0.5，Quarkus BOM 管理 | gradle.lockfile 与真实数据库测试 |
| Spotless / google-java-format | 8.10.4 / 1.36.1 | Java 25 下 spotlessApply/check |
| 辅助工具 | mise.toml/mise.lock | doctor、工具帮助、actionlint、kubeconform |

编译 JDK 与当前可获取的 OCI JRE 都属于 Java 25 LTS；JRE 的补丁版本单独固定，更新后需重新跑镜像冒烟。宿主 Java 27 只作 Wrapper 引导，不决定项目编译目标。没有修改全局 mise Java、Gradle 或 MCP。

Git 使用 mise bootstrap 的发行版维护版本，本环境 Git 2.43.0；本次未安装或升级宿主软件包。其他工具固定精确版本。Serena 来自 PyPI serena-agent 2.1.0，项目元数据指向 github.com/oraios/serena；已验证命令帮助，Java LSP 的完整交互未验收。mise 未完整锁住 Serena 的 Python 传递依赖。

本地 OCI 镜像构建成功；若宿主 ~/.docker/buildx 只读，可设置 `BUILDX_CONFIG="$PWD/.local/buildx"` 再构建，不需要改全局 Docker 配置。实施阶段没有可用 Git 元数据，首次验收用 sourceTreeSha256 标识源码；随后用户授权提交和推送，发布阶段初始化本地仓库。本地验收时 CI/发布工作流仅做静态校验；远端运行结果以 GitHub Actions 为准，真实云部署未执行。

目前 Quarkus 的 Gradle SBOM 生成器提示可能缺少部分许可证信息；SBOM 已生成且包含组件，许可证审查需补充。uber-jar 的上游 OSGi 依赖带重复 about.html，构建可完成。OpenTelemetry 默认不导出；测试框架仍出现上游 API usage 提示，未隐藏警告或跳过测试。
