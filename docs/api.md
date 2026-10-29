# HTTP 示例

业务端口 8080，管理端口 9000。POST `/api/v1/customers/{customerId}/orders`，JSON body 为 `{"description":"demo"}`，返回 201 和 Location。GET 同路径列出最多 20 条，可用 `?limit=1..100`；GET `/{id}` 读取，POST `/{id}/complete` 完成订单，返回 200。重复完成为 409，不存在为 404，无效输入为 400。

失败使用 application/problem+json，包含 code 与 requestId；响应头 X-Request-ID 由服务端生成。组合主键每次都需要客户 ID。当前 API 演示不含身份认证，接入真实业务前在入口加入 OIDC 并校验客户访问权限。

`/web/demo` 是最小 HTML；`/web/redirect` 固定 303 到本地 demo，忽略外部 target 参数。

内部端点 `/q/health/live`、`/q/health/ready`、`/q/metrics` 位于管理端口。liveness 表示进程存活，readiness 包括数据库连接。默认禁用遥测 SDK，无需 Collector；启用时设置 QUARKUS_OTEL_SDK_DISABLED=false 及 OTLP 目标。

server 构建 `./gradlew :server:quarkusBuild`，运行 `java -jar server/build/quarkus-app/quarkus-run.jar`；分发整个 quarkus-app 目录。

描述长度由 core 在去除首尾空白后统一校验，CLI 与 HTTP 使用相同的 1–200 字符规则。错误方法/媒体类型使用正确的 405/415 标题；405 的 Allow 依据 Quarkus 已注册路由生成。Quarkus 升级时必须保留该路由回归测试，因为当前上游路由异常本身未携带 Allow。HTTP 规范见 [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-allow)。
