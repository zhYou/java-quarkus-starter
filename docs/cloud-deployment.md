# 云部署与已有托管 PostgreSQL

模板提供通用 OCI 镜像和 Kubernetes 清单。可在 EKS、ACK、TKE 或其他集群使用；本仓库不创建云资源。发布前替换示例镜像为已推送的不可变摘要，并在集群外完成迁移。当前配置没有在真实云账户运行。

创建既有 Secret `starter-database`，键为 jdbc-url、username、password。服务仅注入运行凭据；迁移凭据通过独立受控作业运行 `./gradlew :core:migrate`。DBA 先建立 app_migration 和 app_runtime，赋予迁移角色 public schema 的所有权与运行角色 USAGE；V2 迁移授予 orders 的 DML。托管服务的管理员账户无需成为应用运行账户。

JDBC 示例：`jdbc:postgresql://db.example:5432/starter?sslmode=verify-full&sslrootcert=/etc/database-ca/ca.pem`。将服务商 CA 挂载到应用，并使用与证书匹配的主机名。CA 可另建 ConfigMap/Secret 并配置 volumeMount；示例清单没有包含真实证书。使用服务私网与最小网络授权，按服务商限制校验 TLS 行为。

| 服务 | 接入核验 | 版本、备份与成本 |
|---|---|---|
| AWS RDS for PostgreSQL / Aurora PostgreSQL-compatible | VPC/安全组、端点与 CA；池连接上限；如用 IAM 动态凭据需单独实现刷新 | 实际区域的 PostgreSQL 主版本、扩展列表、自动备份/PITR 和恢复演练；Aurora 兼容性另测 |
| 阿里云 RDS PostgreSQL | VPC/白名单、SSL 配置和 CA；账户权限；池连接与代理限制 | 区域、引擎小版本、扩展可用性、备份保留和恢复目标；PolarDB 等兼容产品单独验证 |
| 腾讯云 TencentDB PostgreSQL | VPC/安全组、SSL 开启状态和 CA；如使用代理，核验代理端 TLS 和连接池模式 | 引擎/代理版本、备份与恢复能力；克隆实例后复核 SSL 配置 |

三云均不因支持 PostgreSQL 就自动提供 Citus。确认目标实例是否允许安装所需扩展与版本；本模板默认生产接单个托管 PostgreSQL，分片示例独立运行。单库通过迁移与仓储测试并不等于托管扩展、代理或故障切换已验证。

连接池默认每进程 10；总连接预算应包含副本数量、CLI/迁移作业和管理余量。先根据实际负载和数据库连接上限调整，不直接套用示例数值。凭据轮换、CA 轮换、连接断开重试与恢复演练都应在预生产验证。

Kubernetes 清单只用业务 Service 暴露 8080；9000 是内部探针和指标端口。示例 request 为 250m/256Mi，limit 为 1 CPU/512Mi，需按内存、吞吐和延迟测量调整。应用优雅退出 10 秒，Pod 宽限 20 秒。当前 API 无身份认证；OIDC 接入位置见 extensions.md。

官方接入资料：[AWS TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html)、[阿里云 PostgreSQL SSL](https://www.alibabacloud.com/help/en/rds/apsaradb-rds-for-postgresql/configure-ssl-encryption-for-an-apsaradb-rds-for-postgresql-instance)、[腾讯云 PostgreSQL SSL](https://cloud.tencent.com/document/product/409/115986)。
