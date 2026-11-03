# 精简 Gradle 构建配置与命令入口

用户于 2026-11-03 确认实施；本设计取代此前设计中的 conventions 子项目、Daemon JVM criteria 和 mise 任务别名安排。

- `build-logic` 根项目直接承载 Groovy 预编译约定和辅助 Java 类，保留 `operations` 子项目。
- 约定 ID 使用 `java-conventions`、`library-conventions`、`application-conventions`；后两者复用前者，Application 继续配置 Quarkus。
- 删除 `gradle-daemon-jvm.properties`。环境提供运行 Gradle 的 Temurin 25，业务模块通过公共约定和 Foojay resolver 管理 Java 25/Temurin Toolchain。
- build-logic、维护任务和 Serena 复用运行 Gradle 的 JDK，不重复声明版本和发行商。
- 删除与 Gradle 9.8.2 默认行为相同的配置、重复的插件应用和隐式任务依赖。保留非默认的缓存、2 GiB 堆上限、IDE 模型并行，以及项目的编译、测试和安全约束。
- 删除 JVM 参数 `-Dfile.encoding=UTF-8`，保留 `JavaCompile.options.encoding = 'UTF-8'`，明确源码编码。
- Wrapper 属性只保留分发 URL 和 SHA-256；保留生成的 Wrapper 脚本及 Jar。
- 删除 mise 的全部 18 个 Gradle 任务包装；mise 继续管理辅助工具、Git bootstrap 和项目设置。现行文档直接调用 Wrapper，保留分片、CLI、Serena 和危险操作的必要参数。
- 沿用普通检查、真实分片恢复、真实产物和发布校验；本轮不宣称已验收原生 Windows。

采用直接上移 conventions 的方案，避免增加一级项目；operations 的复杂 Java 插件继续独立。统一命令入口选用 Wrapper，避免维护重复的任务映射。
