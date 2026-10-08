# Spring Learning

当前学习： [第 37 课：Micrometer Tracing 与 Zipkin 链路追踪](observability/第%2037%20课：Micrometer%20Tracing%20与%20Zipkin%20链路追踪.md)

使用现有 `Gateway → Order → Feign → User` 调用链，学习 trace ID、span、跨服务日志关联和内部故障定位。

本课使用三个服务的 `tracing` profile。Zipkin 启动入口：`bash observability/run-zipkin.sh`；完整启动顺序与实验见课程文档。
