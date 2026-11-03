# 恢复独立 conventions 子项目

用户于 2026-11-03 要求恢复独立 conventions 项目，使构建逻辑层次更清晰。

- build-logic 根项目只聚合检查和格式化；conventions 承载预编译 Groovy 约定、相关 Java 辅助类和测试；operations 承载复杂 Java 维护插件。
- 保持 conventions 到 operations 的单向依赖，模块继续显式应用 java-conventions、library-conventions、application-conventions。
- 将已有源码和测试整体移回 conventions，沿用既有编译、格式及 TestKit 配置；不新增行为、依赖或 JDK 选择逻辑。
- 沿用上一轮精简后的 Temurin 25 环境前提、源码 UTF-8、默认配置和直接 Wrapper 入口。
- 验证子项目入口及根聚合任务，再运行普通检查、真实分片恢复、产物和发布校验；通过后创建后续 PR 指向 main。

用户已选择独立子项目方案。相较于根项目直接承载约定，多一级目录换取明确的职责边界；这项结构调整取代[上一轮设计](2026-11-03-simplify-gradle-design.md)的上移决定。
