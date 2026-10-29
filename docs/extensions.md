# 扩展位置

core/domain 放纯 Java 业务值和规则；application 放用例、仓储接口与事务边界；persistence 放 JPA 映射与数据库实现；maintenance 放独立迁移和恢复工具。cli 与 server 各自调用 core，不互相依赖。

新微服务复制 server 的启动/配置/部署入口，并按业务所有权拆出独立领域与迁移；不要让多个服务随意修改同一张共享表。通用库只提取稳定的契约，不预建没有消费者的框架。

API 身份认证在 server 加 Quarkus OIDC 扩展，资源边界校验令牌和客户权限。当前 customerId 是演示键，不能视为授权。CLI 自动化使用相应机器身份；不要把生产账号放入 .env.example。

Redis、MQ、对象存储、云 SDK 通过 application 接口和单独适配包加入；增加真实集成测试，外部服务启用显式配置。后台任务要明确幂等、重试、超时和关闭语义。支付跳转示例只展示固定本地 303，真实支付还需签名、回调验证及幂等流程。

复杂前端单建 Node 工程和独立构建/发布流程；未使用前不添加 Node 工具链。Native Image 需独立建立兼容和产物测试，不把 JVM 通过当作原生产物验证。

辅助工具由 mise 管理。Serena 包固定为 PyPI serena-agent 2.1.0；`mise run serena` 从当前仓库启动 stdio MCP，并用 Wrapper 取得的 Java 25 设置子进程 JAVA_HOME。首次运行可获取 Gradle JDK；不修改全局 Java 或 MCP。构建和 CI 不要求 Serena 启动。mise 锁中 Serena 仅固定包版本，未完整锁定其 Python 传递依赖，这是当前工具供应链限制。
