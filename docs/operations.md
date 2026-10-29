# 本地操作和恢复

`mise run doctor` 检查工具和 Docker。`mise run db:up` 启动 PostgreSQL 并迁移；`mise run sharding:up` 启动一协调两 worker 并迁移。凭据随机产生在 .local/db-single.env 或 db-sharding.env，目录 700、文件 600；不要提交、复制到日志或用于生产。

`down` 保留卷，下一次 up 继续使用原密码和数据。`mise run db:reset -- --confirm-local-reset` 或 sharding:reset 删除当前项目记录的卷；没有确认、资源归属不符、外部 JDBC URL 或状态文件异常均会拒绝。脚本没有全局 prune。不要删除有数据环境的 .local 文件；它们记录连接凭据和 Compose 归属。

普通库备份：`mise run db:backup -- --output .local/orders.dump`。恢复：`mise run db:restore -- --input .local/orders.dump`，创建独立 single-restore-xxxxxxxx 目标与端口，不更换源环境配置。备份/恢复使用镜像内同版本 pg_dump/pg_restore。

Citus 示例先停止应用写入，再 `mise run sharding:export -- --output .local/orders.json`；`mise run sharding:restore -- --input .local/orders.json` 新建拓扑、迁移并导入。JSON 仅含本模板 orders；完整校验后才写入，未知版本、损坏输入或非空目标拒绝导入。它不包括角色、扩展、任意业务表或增量日志，不能代替生产备份或 PITR。真实生产备份由选定数据库服务和运维方案提供。

恢复目标名称和配置路径会输出；使用 `python3 scripts/dev.py down --mode single --name single-restore-xxxxxxxx` 停止该目标，reset 加确认删除其卷。失败目标保留诊断，不删除源数据或自动切换源连接。

`mise run dev` 运行 HTTP 开发模式；`mise run cli -- --output=json orders list --customer-id <UUID>` 运行 CLI。选择分片配置可直接 `python3 scripts/dev.py cli --mode sharding -- --output=json orders list --customer-id <UUID>`。构建任务始终调用 Wrapper，实际 Java 路径来自 Gradle Toolchain。

`python3 -m unittest discover -s scripts/tests -v` 验证脚本边界；`./gradlew :server:recoveryTest` 真实验证两种恢复，Citus 源集群停用后再运行目标，最多一套活动分片集群。`shardingCheck` 包含恢复演练。工作区权限不足或 Docker 故障会使测试失败。

Compose 调用显式使用所选目标的配置，父 shell 已导出的源环境端口/密码不会覆盖恢复目标。若管理另一个已记录的目标时仍导出了源 APP_DB_JDBC_URL，先 `unset APP_DB_JDBC_URL`；与所选目标不符的外部连接变量仍会使本地管理操作被拒绝。恢复文件必须恰好包含一个完整 JSON 根对象，拼接对象和尾部垃圾会在使用数据库连接前拒绝。
