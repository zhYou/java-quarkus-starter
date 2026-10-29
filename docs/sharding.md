# Citus 分片示例

固定 Citus 14.2.0 Alpine 镜像和摘要，镜像实际运行 PostgreSQL 18.4。协调节点通过本机 55432 提供与单库相同的 PostgreSQL 协议；两个 worker 仅在专用容器网络内通信。

APP_DB_MODE=sharding 使独立迁移先应用 common，再执行 Citus repeatable SQL。orders 按 customer_id 分成 8 个逻辑分片；重复迁移保留数据，发现不兼容的分布定义会失败。组合主键包含分布键，查询/事务应限制在一个客户内；示例没有跨客户事务或任意跨分片 JOIN 承诺。

管理初始化创建扩展、注册 worker，并在各节点创建角色和节点间凭据。应用只使用 app_runtime。开发网络内节点连接关闭 TLS，宿主端口绑定回环地址；生产需要网络隔离和 TLS。本例没有副本、高可用、自动扩容或生产备份。

`./gradlew shardingCheck` 顺序运行真实多节点仓储、物理路由、重启保留与 CLI 测试。路由测试直接读取两个 worker 的物理分片，校验总量、无重复和同客户共置，单节点替代不能通过。

参考：[Citus 认证元数据](https://learn.microsoft.com/en-us/postgresql/citus/api-metadata?view=citus-14)。
