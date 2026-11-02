# CLI 使用与设计约定

业务 CLI 保留为独立 Quarkus 应用，调用 core 中的同一套业务规则。设计参考 [Command Line Interface Guidelines（clig.dev）](https://clig.dev/)。该指南用于评估具体需求，本项目按已有契约选择适用建议。

## 使用

先执行 `./gradlew :cli:quarkusBuild`，再使用 Java 25 运行：

```bash
java -jar cli/build/starter-cli-runner.jar --help
java -jar cli/build/starter-cli-runner.jar --version
java -jar cli/build/starter-cli-runner.jar orders create --help
java -jar cli/build/starter-cli-runner.jar --output=json orders list \
  --customer-id 00000000-0000-0000-0000-000000000001
```

业务执行通过 APP_DB_JDBC_URL、APP_DB_USERNAME、APP_DB_PASSWORD 连接数据库。帮助、版本及输入预检不需要数据库，也不启动 Quarkus。现有输出选择参数保持为 `--output=text|json`。

本地开发便捷入口自动构建 Jar，并读取选定的项目私有配置：

```bash
mise run cli -- --args='--output=json orders list --customer-id 00000000-0000-0000-0000-000000000001'
./gradlew -q --console=plain runCli -PdbMode=sharding \
  --args='--output=json orders list --customer-id 00000000-0000-0000-0000-000000000001'
```

Gradle 入口需要先创建本地环境。它采用构建失败语义，应用非零退出码会导致 Gradle 失败。需要精确业务退出码、离线帮助或稳定管道接口时直接运行 Jar。

## 输出、错误和自动化

| 结果 | Jar 退出码 |
|---|---|
| 成功、帮助、版本 | 0 |
| 内部错误、启动或连接失败 | 1 |
| 参数或业务输入错误 | 2 |
| 订单不存在 | 3 |
| 订单状态冲突 | 4 |

- 默认文本面向人工阅读；`--output=json` 的 stdout 只含一个 JSON 结果，包括结构化错误对象。
- 诊断写 stderr，不混入 SQL、堆栈或凭据；对失败给出可采取的操作提示。
- 各子命令保持一致的命名、选项和帮助规则。参数和输出字段是接口，变更时考虑脚本兼容性。
- 非交互调用不增加阻塞提示。危险的本地维护操作使用显式确认参数，数据库恢复总是新建目标。
- 不因为引用指南就自动新增别名、交互模式、分页或彩色输出；按具体需求补充行为测试后实现。

`packageCheck` 使用 Gradle 管理的 JDK 启动真实 Jar，验证离线帮助、错误 JSON、连接失败与凭据脱敏。业务入口仍启用 CDI 请求上下文，customerId 不代表授权。
