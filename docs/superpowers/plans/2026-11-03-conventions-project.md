# 恢复独立 conventions 子项目实施计划

依据：[用户确认的结构调整](../specs/2026-11-03-conventions-project-design.md)。

基线：origin/main `ea3882f`；分支：`refactor/261103-conventions-project`；PR 目标：main。复用现有原生隔离 worktree，保留已有精简结果和三个插件 ID。

- [x] 复核 PR #7 已合并及 CI 通过，确认干净工作区与基线。
- [x] 基线 buildLogicCheck 通过；执行 :build-logic:conventions:check，确认恢复前因项目不存在而失败。
- [x] 将源码、辅助类、测试和构建配置移回 conventions，根项目恢复聚合职责。
- [x] 更新构建说明、README 与 AGENTS，记录替代关系。
- [x] 验证 conventions 子项目入口、格式化和根聚合检查。
- [x] 串行运行 check、shardingCheck、packageCheck、assembleRelease，核对产物校验与源码标识。
- [x] 提交并完成一次独立审阅，推送后创建面向 main 的后续 PR，保留 worktree。

交付：[PR #8](https://github.com/zhYou/java-quarkus-starter/pull/8)。完整测试及独立审阅均通过，详见[验证记录](../../verification.md)。
