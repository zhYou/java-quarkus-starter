# 本地操作和恢复

所有本地管理操作通过 Gradle Java 任务执行，mise 保留快捷任务名称。`mise run doctor` 检查工具和 Docker；`mise run db:up` 启动并迁移 PostgreSQL；`mise run sharding:up` 启动并迁移一协调两 worker 的 Citus 示例。

凭据随机生成在 `.local/db-single.env` 或 `db-sharding.env`，目录权限 700、文件权限 600。已有 Python 工具生成的本地状态格式继续兼容。状态记录连接凭据与 Compose 归属，应保留，不用于生产或复制到日志。

| 操作 | Gradle 入口 |
|---|---|
| 启动并迁移 | `./gradlew dbUp` |
| 停止并保留数据卷 | `./gradlew dbDown` |
| 确认删除所选环境的数据卷 | `./gradlew dbReset -PconfirmLocalReset=true` |
| 对已存在的本地环境迁移 | `./gradlew dbMigrate` |
| 普通库备份 | `./gradlew dbBackup -Poutput=.local/orders.dump` |
| 恢复到新环境 | `./gradlew dbRestore -Pinput=.local/orders.dump` |

`-PdbMode=single|sharding` 选择模式，默认 single；`-PdbName=<已记录名称>` 选择已有目标。mise sharding 别名自动传入 sharding 模式。没有确认、归属不符、外部 JDBC URL 或异常状态时拒绝重置；没有全局 prune。

普通库使用镜像内同版本 pg_dump/pg_restore。恢复自动创建 single-restore-xxxxxxxx 名称和新端口，不修改源配置。恢复目标名称和配置路径输出到 stderr；使用 `./gradlew dbDown -PdbName=single-restore-xxxxxxxx` 停止该目标，reset 时再加确认属性。

Citus 示例导出前需停止应用写入：

```bash
mise run sharding:export -- -Poutput=.local/orders.json
mise run sharding:restore -- -Pinput=.local/orders.json
```

恢复新建 sharding-restore-xxxxxxxx 拓扑，迁移后导入。JSON 仅用于本模板 orders 示例，不包含角色、扩展、任意业务表或增量日志。未知版本、损坏输入、尾部垃圾和非空目标拒绝导入；不能替代生产备份或 PITR。

Compose 显式使用目标环境配置，父 shell 中已导出的源端口或密码不会覆盖新恢复目标。管理另一个已记录目标时若仍导出源 APP_DB_JDBC_URL，需要先 unset；外部连接与目标不符时，本地操作仍会拒绝。失败目标保留诊断，不删除源数据或切换源连接。

`mise run dev` 启动使用本地配置的 HTTP 开发模式，并关闭自动 DevServices，避免启动未记录归属的附加容器；`./gradlew :server:quarkusDev` 仍可用于显式配置的常规 Quarkus 开发模式，添加 `-PlocalDb=true` 可加载所选本地环境。CLI 参数与退出码见 [CLI 文档](cli.md)。Serena 使用 `mise run serena`，附加参数采用 `--args`。

Java 单元测试和 Gradle TestKit 验证操作边界；`./gradlew shardingCheck` 验证真实分片与两种恢复，`packageCheck` 验证真实 Jar。外部状态操作每次执行；构建内通过共享服务串行修改本地环境，不跳过 Docker 故障。
