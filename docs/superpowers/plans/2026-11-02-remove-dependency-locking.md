# Remove Dependency Locking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 Git 跟踪的 9 个锁与依赖校验文件及其关联机制。

**Architecture:** 保留 BOM、版本目录与 mise.toml 的版本声明，取消 Gradle 依赖锁定和校验 XML。发布源码标识排除弃用生成文件，继续跟踪真实版本配置。

**Tech Stack:** Gradle 9.8.2、Groovy convention plugins、Java/Temurin 25、JUnit 6、mise。

**Spec:** docs/superpowers/specs/2026-11-02-remove-dependency-locking-design.md

## Global Constraints

- 基线 main@981ac46f3c10ecff3d58bf677a7d14b725b84e5b；任务分支 chore/261102-remove-dependency-locking；PR 目标 main。
- 使用工具托管的独立 worktree，所有构建和项目 JDK 操作经 ./gradlew，Gradle 命令串行执行。
- 仅清理受跟踪文件；本地被忽略的缓存、产物、凭据和工作记录不在清理范围内。
- 不改变业务 API、CLI、数据库或显式依赖版本；保留 Wrapper 与发布产物的 SHA-256 校验。

## Review Focus

- 根项目、模块和 included build 遗留生成文件都不得改变发布源码标识。
- 版本目录与 mise.toml 变化必须改变发布源码标识。
- TestKit 缺少校验 XML 时仍验证编译告警和配置缓存。
- 项目 mise 配置关闭锁文件，不修改全局设置。
- 真实分片、产物和发布任务必须在没有这些文件时通过。

### Task 1: Remove tracked dependency locks and verification

**Files:**
- Delete: mise.lock、gradle/verification-metadata.xml，以及根项目、core、cli、server、两个 build-logic 子项目的全部 7 个 *.lockfile。
- Modify: Gradle convention plugins、两个 build-logic 子项目构建脚本、LocalDevelopmentPlugin、mise.toml、.gitignore。
- Modify: ReleaseFiles、ReleaseFilesTest、ConventionsTest，以及 AGENTS.md、README.md、docs/gradle.md、docs/upgrading.md、docs/compatibility.md。

**Interfaces:**
- 保持 ReleaseFiles.sourceDigest(Path root) 的签名；版本配置属于源码，弃用生成文件不属于源码标识。
- 不引入公开 API、替代锁机制或新的清理命令。

- [x] Step 1: 在原实现上运行 ReleaseFilesTest，确认基线通过。
- [x] Step 2: 将旧的锁文件影响源码摘要测试改为弃用文件不影响源码摘要的回归测试，覆盖根项目、模块、included build 和校验 XML；另验证版本目录与工具配置变化影响摘要。
- [x] Step 3: 运行 ReleaseFilesTest，预期弃用文件测试失败，确认失败来自现有摘要纳入了生成文件。
- [x] Step 4: 删除 9 个文件；移除所有 Gradle 锁定配置；设置项目 mise lockfile = false；精确忽略 gradle.lockfile、settings-gradle.lockfile、/mise.lock 和 /gradle/verification-metadata.xml。
- [x] Step 5: 移除 TestKit 的 XML 复制；清理发布源码清单并在摘要中排除弃用文件；更新当前使用文档和 AGENTS.md，保留历史记录。
- [x] Step 6: 运行 ./gradlew spotlessApply，然后运行 ./gradlew check shardingCheck packageCheck assembleRelease --no-daemon；预期全部通过且发布产物校验正常。
- [x] Step 7: 验证 mise settings get lockfile 输出 false；检查删除文件未重新生成、精确忽略规则生效，git diff --check 通过。
- [x] Step 8: 审阅差异、提交（Agent: Codex），完成一次独立整分支审阅并处理重要问题。

## 验证结果

- ReleaseFilesTest 基线通过；新增回归测试在旧实现上出现 9 个预期失败，修改后全部通过。
- ./gradlew spotlessApply 通过。
- ./gradlew check shardingCheck packageCheck assembleRelease --no-daemon --console=plain 通过，用时 2 分 41 秒；62 个测试，0 失败、0 错误、0 跳过。
- SHA256SUMS 中 5 个文件校验通过；两个 CycloneDX SBOM 分别包含 269、439 个组件，SOURCE.json 含有效源码摘要。
- mise settings get lockfile 输出 false；9 个移除文件未重新生成，忽略规则生效，git diff --check 通过。
- 仍出现已有的上游 OpenTelemetry/Micrometer 和 SBOM 许可证提示，未跳过或隐藏检查。
- 独立整分支审阅未发现 Critical、Important 或 Minor 问题，未评判行为为空。

## 交付

验证和审阅通过后推送任务分支 chore/261102-remove-dependency-locking，创建指向 main 的 PR。保留工具托管 worktree /home/zhouyou/.codex/worktrees/cf8b/java-quarkus-starter 供审查修复，不自动合并。
