# Gradle 构建逻辑与开发工具设计

用户于 2026-11-02 确认实施会话中的完整方案。

- 所有构建脚本使用 Groovy DSL，独立 included build `build-logic` 分为 conventions、operations。
- 公共 Java、Library、Quarkus 应用约定使用预编译 Groovy 插件；复杂任务和本地环境管理使用 Java。
- 迁移项目自维护的 Python 脚本及其测试，保留现有本地状态格式、资源归属、凭据权限、独立恢复目标和业务 CLI。
- Gradle 启用构建缓存和配置缓存，移除 workers.max 与 parallel 设置；不缓存外部状态操作。
- Temurin 25 运行构建，Native 使用 Mandrel 25 容器；本次不建立 Native 发布流水线。
- CLI Jar 保留现有参数、JSON 和退出码；Gradle runCli 为开发入口，采用构建失败语义。
- 全仓文本 LF；gitignore 按功能分组并添加中文注释。
- 项目文档引用 Gradle 官方手册、最佳实践总索引与 clig.dev；明确 Groovy 是项目选择。
- 验证涵盖 Java/TestKit、普通检查、分片恢复、真实产物、发布校验、配置及构建缓存。
