# 移除仓库版本锁与依赖校验

## 目标与范围

精简受 Git 跟踪的生成文件及其维护机制：删除 mise.lock、7 个 Gradle *.lockfile 和 gradle/verification-metadata.xml，共 9 个文件。本地被忽略的缓存、构建产物、凭据和工作记录不在范围内。

保留 BOM、版本目录、mise.toml 的版本声明，以及 Gradle Wrapper、Temurin 25、Wrapper 分发包校验和发布产物校验。业务规则、API 和 CLI 契约保持不变。

## 方案选择

- 完整移除机制：删除文件、取消依赖锁定及校验文件引用，同步测试、发布源码标识和文档，避免继续维护这些生成文件。用户选择并批准此方案。
- 仅取消 Git 跟踪：保留个人本地生成锁和校验文件的能力，需要维护文件可选时的行为。未采用。

## 设计

移除应用、维护运行时和 included build 的 Gradle 依赖锁定，在项目级设置 mise lockfile = false。为被移除文件添加精确忽略规则，不使用 *.lock 这样的宽泛规则。

TestKit 不再复制校验 XML。发布源码摘要排除已弃用的生成文件，继续跟踪版本目录、工具配置和真实源码；同一源码不会因为个人重新生成锁或校验文件而产生不同标识。

更新 AGENTS.md 和当前使用文档，删除维护依赖锁与依赖校验元数据的步骤。历史设计、计划与验证记录作为历史证据保留。

## 验收

先添加发布源码摘要回归测试，确认弃用文件不影响标识，版本声明变化影响标识，并观察测试在原实现上失败。

串行执行 ./gradlew spotlessApply 和 ./gradlew check shardingCheck packageCheck assembleRelease --no-daemon。确认 TestKit 在缺少校验文件时通过，发布 SOURCE.json、SBOM 和 SHA256SUMS 正常，上述文件没有重新生成或进入 Git。

## 约束与默认决定

仅修改独立 worktree，不修改全局 Java、mise 或 MCP 配置。保留现有显式版本声明，不新增替代锁定系统。完整解析版本与依赖内容校验基线不再由仓库提交。

检查通过后提交并推送任务分支、创建指向 main 的 PR，保留 worktree，不自动合并。
