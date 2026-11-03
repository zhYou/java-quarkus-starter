# 精简 Gradle 构建配置与命令入口实施计划

依据：[已确认设计](../specs/2026-11-03-simplify-gradle-design.md)。

基线：`origin/main`，`93486fe`；分支：`build/261103-simplify-gradle`；PR 目标：`main`。

## 约束

所有构建和项目 JDK 查询使用 `./gradlew`，同一工作区串行执行。环境提供 Temurin 25，不修改全局 Java、mise 或 MCP 配置。源码编码保留 UTF-8；保留严格编译、业务边界、测试标签、Compose 归属和重置确认。

## Task 1: 构建结构、JDK 与默认配置

- [x] 在新 worktree 运行 buildLogicCheck，确认基线。
- [x] 先将现有 TestKit 用例改为新 ID，确认因插件无法解析而失败。
- [x] 上移 conventions，更新三个插件 ID、模块引用和测试路径；根检查覆盖自身与 operations。
- [x] 删除 Daemon JVM criteria 和 build-logic 的重复 Toolchain 声明；维护任务复用 Gradle JDK，JavaExec 使用默认 launcher。
- [x] 删除默认 fork 数、归档设置、group/version 重复赋值、重复 base 应用、included build 名称、隐式依赖和 canBeResolved=true。
- [x] JVM 编码参数删除，源码编码保留；Wrapper 属性仅保留分发 URL 和 SHA-256。
- [x] 使用 Temurin 25 执行 spotlessApply、buildLogicCheck，确认新 ID 和现有行为通过。

## Task 2: mise 与文档

- [x] 删除全部 mise Gradle 任务包装，保留工具、bootstrap 和项目设置。
- [x] 更新 README、AGENTS 和现行文档中的命令、JDK 前提、构建职责及升级步骤；历史验收记录保留。
- [x] 保留分片的 dbMode、CLI/Serena 的安静纯文本参数，以及 reset 确认参数。
- [x] 核对 TOML 可解析、任务为空、辅助工具声明有效，现行文档不再使用 mise 任务别名。

## Task 3: 验证与交付

- [x] 确认 Gradle 与 javaPath 使用 Temurin 25。
- [x] 串行执行 check、shardingCheck、packageCheck、assembleRelease，并检查 SHA256SUMS。
- [x] 记录验证结果及限制，审查差异、忽略规则和工作区内容。
- [ ] 提交后完成一次独立审阅，处理实质性问题，按改动范围补充验证。
- [ ] 推送任务分支并创建面向 main 的 PR，报告链接、验证结果、分支与 worktree。
