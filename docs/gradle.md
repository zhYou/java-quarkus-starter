# Gradle 构建约定与官方资料

项目使用 Gradle Wrapper 9.8.2、Groovy DSL 和 Temurin 25。环境提供运行 Gradle 的 JDK，业务模块通过 Toolchain 选择 JDK。所有构建与项目 JDK 查询均通过根目录的 `./gradlew`，同一工作区串行运行命令。mise 只管理辅助工具与 Git bootstrap。

## 官方参考

- [最佳实践总索引](https://docs.gradle.org/current/userguide/best_practices_index.html)、[通用最佳实践](https://docs.gradle.org/current/userguide/best_practices_general.html)
- [官方用户手册](https://docs.gradle.org/current/userguide/userguide.html)、[本项目版本手册](https://docs.gradle.org/9.8.2/userguide/userguide.html)
- [构建组织与 build-logic](https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html)、[Composite Builds](https://docs.gradle.org/current/userguide/composite_builds.html)
- [Convention Plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html)、[Groovy DSL](https://docs.gradle.org/current/userguide/groovy_build_script_primer.html)、[Java API](https://docs.gradle.org/current/javadoc/)
- [版本目录](https://docs.gradle.org/current/userguide/version_catalogs.html)
- [构建性能](https://docs.gradle.org/current/userguide/performance.html)、[配置缓存要求](https://docs.gradle.org/current/userguide/configuration_cache_requirements.html)、[TestKit](https://docs.gradle.org/current/userguide/test_kit.html)

`current` 用于了解最新建议；实施时以 Wrapper 版本的 API 和兼容验证为准。官方通用建议偏好 Kotlin DSL，本项目按维护者的明确选择使用 Groovy DSL。

## 组织与职责

`build-logic` 是通过 `pluginManagement.includeBuild` 接入的独立构建。`conventions` 子项目承载 `src/main/groovy` 中的预编译约定，以及相关 Java 辅助类和测试；`operations` 子项目承载复杂维护插件。build-logic 根项目只聚合两者的检查和格式化任务。

| 插件项目 | 插件 | 职责 |
|---|---|---|
| conventions | java-conventions | JDK、编译器、JUnit、Spotless、Quarkus BOM |
| conventions | library-conventions | 公共 Java 约定、Java Library 与 Test Fixtures，供 core 使用 |
| conventions | application-conventions | 公共 Java 约定、Quarkus、SBOM、分片测试，供 cli/server 使用 |
| operations | starter.local-development | 本地环境、开发运行和 Serena；复杂逻辑使用 Java |
| operations | starter.release | 发布归档、源码标识、校验文件；不执行发布 |

模块脚本显式应用约定插件，保留业务依赖与模块专有任务。根项目只组织聚合任务。共享配置不通过 `allprojects`、`subprojects` 或 `afterEvaluate` 注入。

版本目录由主构建和 included build 显式共享。外部插件在 conventions 的依赖中通过目录别名解析；预编译插件内按插件 ID 应用。conventions 依赖 operations 提供的本地环境支持，operations 不反向依赖 conventions。构建逻辑自身的 Java 测试和格式设置写在其构建脚本中，避免插件自举依赖循环。主构建的 group、version 由 gradle.properties 自动设置。

`buildLogicCheck` 通过 build-logic 根项目校验 conventions 与 operations，主构建的 `check` 包含该任务。TestKit 使用独立临时项目、共享依赖缓存，遵循调用者的 offline 设置；根 `spotlessApply` 同时格式化插件代码和应用代码。依赖版本由版本目录与 Quarkus BOM 管理，主构建和 included build 均不维护依赖锁或校验元数据。

## 缓存与任务边界

`gradle.properties` 保留不同于 Gradle 9.8.2 默认值的构建缓存、配置缓存、IDE Tooling 并行和 2 GiB 堆上限。Daemon、文件系统监听、worker 数量和单个测试进程沿用默认值；归档使用 Gradle 9 默认的稳定顺序与固定时间戳。Java 25 默认字符集为 UTF-8，JVM 不再重复声明；源码编译仍显式设置 UTF-8，避免外部默认编码覆盖。

自定义任务通过声明的属性和注入的进程服务执行，不在执行阶段访问 Project，也不在 Gradle 内再次调用 Wrapper。维护任务在执行时读取私有配置，不将凭据放到任务参数或日志中。数据库操作无缓存输出，每次调用均执行，并通过共享服务串行修改本地环境。

Quarkus 3.40 的开发模式任务保留了无法序列化的 Configuration/SourceSet，因此约定插件仅将 quarkusDev 显式标记为配置缓存不兼容。本地环境按请求延迟提供，在 Quarkus 定型输入前加载，并关闭自动 DevServices，仅使用已记录的 Compose 环境；不全局关闭缓存或启用 warning 模式。普通检查不包含 sharding/recovery/packaged 标签，专用任务保留原有顺序。同模块普通测试先于分片测试，避免同时占用 Quarkus 测试端口；此约束不会让单独的分片检查执行普通测试。

## JDK 与 Native

通过 JAVA_HOME 或 IDE 的 Gradle JVM 设置提供 Temurin 25。项目不提交 Daemon JVM criteria；build-logic、维护命令和 Serena 复用运行 Gradle 的 JDK。业务模块只在 java-conventions 中声明 Java 25/ADOPTIUM，Foojay resolver 在没有匹配的本地 Toolchain 时自动下载。JavaExec 使用插件提供的 Toolchain 默认值，自定义进程任务仅保留必要的可执行文件映射。

`./gradlew --version` 查询 Gradle 的运行 JVM，`./gradlew -q :core:javaPath` 查询业务 Toolchain。Wrapper 属性只保留分发 URL 和 SHA-256；标准存储路径、超时、重试与 URL 验证使用 Wrapper 默认值。

Quarkus 3.40.1.1 的源码默认原生构建版本为 JDK 25，最低支持 GraalVM/Mandrel 25.0。依据：[版本源码](https://github.com/quarkusio/quarkus/blob/3.40.1.1/core/deployment/src/main/java/io/quarkus/deployment/images/ContainerImages.java)、[官方公告](https://quarkus.io/blog/mandrel-25-minimum-version/)。部分版本化指南仍含 JDK 21 的旧示例，应结合对应版本源码核对。

JVM 构建继续使用 Temurin 25。Linux Native 构建使用官方 Mandrel 25 容器，示例固定清单摘要：

```bash
./gradlew :cli:quarkusBuild :server:quarkusBuild \
  -Dquarkus.native.enabled=true \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25@sha256:4af907423955d43beb791ee247516e13128fc880b4e58f6a66171a90f850e1fb
```

此命令是后续 Native 工作的构建起点；当前产物检查和发布流程验证 JVM Jar，不代表已验收原生产物。Native 需要另行建立产物与业务兼容测试；镜像升级时重新核对官方摘要。
