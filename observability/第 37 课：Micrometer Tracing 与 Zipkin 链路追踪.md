# 第 37 课：Micrometer Tracing 与 Zipkin 链路追踪

## 一、请求经过三个服务，怎样找到慢在哪里

前面的课程已经把网关、订单服务和用户服务连起来了：

```text
客户端
   ↓ GET /api/orders/100
gateway-server:9120
   ↓ GET /orders/100
order-service:9102
   ↓ Feign GET /users/1
user-service:9101
```

网关日志能告诉我们请求总共用了多少时间，但定位一次请求还需要回答：订单服务和用户服务中哪些日志属于它？时间主要花在哪段调用？接口返回成功时，内部是否发生过错误和降级？

本课给现有 HTTP 调用链增加追踪。先直接访问订单服务，理解两服务链路；再从网关进入，查看三服务链路。

## 二、Trace 和 Span

| 概念 | 含义 | 本课例子 |
| --- | --- | --- |
| Trace | 一次请求所经过的一整条执行链 | 从网关接收请求，到订单服务调用用户服务 |
| Trace ID | 这条链的共同标识 | 三个服务用同一个 ID 关联日志 |
| Span | 链中的一次操作，记录开始、结束和结果 | 接收 HTTP 请求，或者发送一次 Feign 请求 |
| Span ID | 某段操作的标识 | 同一条 trace 中不同操作通常有不同 span ID |
| Parent Span ID | 当前操作与上游操作的关系 | 用户服务请求来自订单服务的客户端调用 |

一个服务可以产生多个 span，所以三个服务不等于三个 span。这里的 ID 也不是订单 ID：订单 `100` 可以被查询多次；请求没有携带上游追踪上下文时，会创建新的 trace。[Micrometer 术语说明](https://docs.micrometer.io/tracing/reference/glossary.html)。

```text
同一 Trace ID
└─ Gateway 接收请求
   └─ Gateway 转发请求
      └─ Order 接收请求
         └─ CircuitBreaker 执行调用
            └─ Feign 调用 User
               └─ User 接收请求
```

这是简化结构；实际还会出现 Spring Security 等操作的 span。Zipkin 的时间线展示各段操作发生的时间和持续时间。父操作往往包含子操作的等待时间，阅读时不要把所有 span 的耗时简单相加。

## 三、应用负责采集，Zipkin 负责展示

本课使用 Spring Boot `4.1.1`、Spring Cloud `2025.1.3`：

| 组件 | 做什么 |
| --- | --- |
| Micrometer Observation | 观察 HTTP 等操作，记录操作的开始、结束和异常 |
| Micrometer Tracing | 提供追踪接口，并管理当前 trace/span 上下文 |
| Brave | 本课使用的追踪实现 |
| Zipkin 上报组件 | 将完成的 span 发给 Zipkin |
| Zipkin Server | 接收追踪数据，提供查询和时间线界面 |

应用处理业务时采集数据，随后向 Zipkin 上报：

```text
Gateway ──业务 HTTP──→ Order ──业务 HTTP──→ User
   │                    │                  │
   └────────────── span 上报 ────────────────┘
                        ↓
                   Zipkin:9411
```

业务请求仍然由网关和微服务处理。三个应用分别将完成的 span 异步上报，Zipkin 按 trace ID 组织查询结果。[Zipkin 架构](https://zipkin.io/pages/architecture.html)。

## 四、代码中增加了什么

### 1. 追踪依赖

三个服务都添加了 Spring Boot 的 Zipkin starter：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-zipkin</artifactId>
</dependency>
```

它集成 Brave 与 Zipkin 上报。`order-service` 同时补充 Actuator；网关和用户服务原本已有 Actuator。依赖版本继续由项目的 Boot parent 和 Cloud BOM 管理。[Spring Boot 链路追踪](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)。

订单服务增加了 `io.github.openfeign:feign-micrometer`。当该组件和 `ObservationRegistry` 存在时，OpenFeign 可以为调用添加观测能力，让客户端调用参与追踪。[OpenFeign Micrometer 支持](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html#micrometer-support)。

### 2. tracing profile

各服务的 `application-tracing.yml` 包含：

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
    propagation:
      type: W3C
    export:
      zipkin:
        enabled: true
        endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

logging:
  include-application-name: false
  pattern:
    correlation: "[${spring.application.name:},traceId=%X{traceId:-},spanId=%X{spanId:-}] "
```

`probability: 1.0` 表示课堂中采样 100%，方便每次实验都能查到链路。真实系统应根据流量和存储成本选择采样率，未采样的请求仍然执行业务，只是不完整地保留追踪数据。

`endpoint` 是上报接口；浏览器查看数据时打开 `http://localhost:9411`。Boot 4 使用 `management.tracing.export.zipkin.*`，需要按本项目版本填写属性。本项目默认关闭 Zipkin 上报，启用 `tracing` profile 才会发送数据。

用户服务的这个 profile 还关闭 Config Client 和 Cloud Bus，使用本地端口 `9101`、Eureka 地址和“链路追踪练习用户”。这样本课只需要 Eureka、Zipkin 和参与实验的 HTTP 服务。

用户服务基础配置用 `---` 分成两段，把 Config Server 的导入放在 `spring.config.activate.on-profile: "!tracing"` 下。配置导入在启动早期处理，直接在 profile 中覆盖导入地址不能阻止基础文件先加载它；这里按模式控制导入文档是否生效。[Spring Boot 配置文档激活](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.activation-properties)。

### 3. 日志中的 MDC

MDC 是日志框架为当前执行上下文保存字段的位置。追踪组件把当前 `traceId`、`spanId` 放入 MDC，`%X{traceId}` 就能把它们打印出来。

概念示例：

```text
[order-service,traceId=T,spanId=A] 查询订单：orderId=100
[user-service,traceId=T,spanId=B] 查询用户：userId=1
[order-service,traceId=T,spanId=A] 订单用户信息：orderId=100 userId=1
```

实际 ID 是十六进制字符串。找到 `T` 后，可以在不同服务的日志中搜索同一 ID；启动日志等没有请求上下文的日志，ID 可能为空。

仅修改日志格式不会建立链路。必须先产生追踪上下文，并在调用其他服务时继续传递它。

### 4. HTTP 上怎样传递上下文

本课统一使用 W3C 格式，通过 `traceparent` 请求头传递 trace ID、当前操作 ID 和采样标志：

```text
traceparent: 00-<32位traceId>-<16位上游spanId>-01
```

下游服务读取这个请求头，继续加入同一条 trace。客户端的观测能力负责注入，上游和下游需要使用兼容的传播配置。

四段分别是版本、trace ID、上游 span ID 和标志；这里 `01` 的最低位表示采样。[W3C Trace Context](https://www.w3.org/TR/trace-context/#traceparent-header)。

网关还把当前 trace ID 放进响应头 `X-Trace-Id`，方便调用方查找这次请求。它是本项目提供的查询提示；HTTP 调用之间传播上下文使用的是 `traceparent`。

## 五、启动实验环境

每个服务使用一个终端。从仓库根目录开始，按以下顺序启动。

### 1. Zipkin

```bash
cd observability
bash ./run-zipkin.sh
```

脚本首次下载固定版本 `3.6.1` 的可执行 JAR，缓存在 `observability/.cache`，后续重复使用。它通过 Java 直接启动，不需要 Docker。该缓存被 Git 忽略。

打开 [Zipkin 界面](http://localhost:9411)。本地实验使用内存存储，重启 Zipkin 后历史链路会清空。[Zipkin Quickstart](https://zipkin.io/pages/quickstart.html)。

### 2. Eureka

```bash
cd discovery-server
bash ./mvnw spring-boot:run
```

打开 [Eureka](http://localhost:9761)，后续检查订单服务和用户服务是否已注册。

### 3. 用户服务

```bash
cd user-service
bash ./mvnw spring-boot:run -Dspring-boot.run.profiles=tracing
```

### 4. 订单服务

```bash
cd order-service
bash ./mvnw spring-boot:run -Dspring-boot.run.profiles=tracing
```

确保终端显示 `tracing` profile 已启用，且 Eureka 中有 `USER-SERVICE` 和 `ORDER-SERVICE`。服务注册和客户端拉取列表需要一点时间。

本节直接访问订单服务，不需要启动网关和认证服务，也不需要 JWT。

## 六、实验一：查出 Order → User 的链路

请求一个订单：

```bash
curl -i http://localhost:9102/orders/100
```

订单服务会查询用户 `1`，响应中的用户名称应是“链路追踪练习用户”。如果返回 `userId=0` 对应的降级用户，先检查用户服务和服务发现。

观察两个终端：

1. 订单服务打印“查询订单”和“订单用户信息”。
2. 用户服务打印“查询用户”。
3. 这些日志中的 `traceId` 应一致。

稍等片刻，让完成的 span 上报到 Zipkin。在 Zipkin 界面选择 `order-service`，时间范围选择最近的请求，点击 **Run Query**。打开刚才请求的详情，查看订单服务接收请求、Feign 客户端调用和用户服务处理。

也可以从日志复制 trace ID，在界面的 trace ID 查询入口直接定位。相同业务请求重复执行时，用 trace ID 区分每次调用，不要只按路径判断。

## 七、实验二：加入 Gateway

先按第 29 课启动 `auth-service`，它仍使用原有数据库与认证配置。再启动网关：

```bash
cd gateway-server
bash ./mvnw spring-boot:run -Dspring-boot.run.profiles=tracing
```

通过原有 `/api/auth/register`、`/api/auth/login` 流程获得有效 JWT；已有账户可以直接登录。将登录结果中的 token 放到下面的请求头：

```bash
curl -i http://localhost:9120/api/orders/100 \
  -H 'Authorization: Bearer 替换为登录获得的token'
```

网关仍校验 JWT。响应应包含 `X-Trace-Id`，复制它到 Zipkin 查询，查看 Gateway → Order → User 的完整链路，并对照三个服务日志的 trace ID。

本课没有为认证服务增加追踪，因此登录请求不会展示与订单查询相同的三服务结构。

## 八、实验三：返回 200，也可能发生过内部失败

只停止本次实验启动的用户服务终端，保留 Zipkin、Eureka 和订单服务，再请求：

```bash
curl -i http://localhost:9102/orders/100
```

第 24 课的 fallback 会返回 `id=0`、名称为“用户服务暂不可用”的用户。订单接口仍可返回 HTTP `200`，但这次结果已经发生降级。

在订单服务日志中找到“调用 user-service 失败”及对应 trace ID，再到 Zipkin 查看同一条链路。在本轮连接拒绝实验中，`circuit-breaker` 带有 `error`，随后出现 `circuit-breaker fallback`，外层 HTTP SERVER span 的状态仍是 `200`。失败没有到达用户服务，因此没有这次调用的用户服务 SERVER span。

停止服务后，发现列表可能暂时还缓存旧实例，这时会出现连接失败；列表更新后可能变为找不到可用实例。错误位置和 span 结构可能不同，以实际链路和异常为准。恢复用户服务后，等待服务发现更新，再确认真实用户信息恢复。

## 九、常见问题

### 1. 业务有响应，Zipkin 没有数据

检查服务是否带 `tracing` profile 启动、Zipkin 是否监听 `9411`、上报地址是否包含 `/api/v2/spans`，以及终端是否有上报连接异常。上报是异步的，查询时可以稍等并扩大时间范围。默认关闭上报时，日志可以有 trace ID，但 Zipkin 收不到数据。

### 2. Order 和 User 出现不同 trace ID

检查 `feign-micrometer` 和 `ObservationRegistry` 是否存在，是否关闭了 `spring.cloud.openfeign.micrometer.enabled`，两端是否使用一致的 W3C 配置。

请求可能通过 CircuitBreaker 或其他执行器切换线程，需要继续传播上下文。新建线程、异步任务或自行创建未受自动配置管理的 HTTP 客户端，都可能造成链路断开。此时应修复执行与客户端的上下文传播，不能通过手工把两个日志 ID 改成一样来替代。

本项目的 Cloud CircuitBreaker `5.0.3` 已自动接入 ObservationRegistry，并在执行调用时恢复上游 Observation；正常实验中，熔断器与 Feign、User 的 span 保留父子关系。Gateway WebMVC 使用自动配置的 `RestClient.Builder`，转发也参与追踪，所以本课无需手工复制追踪头。

### 3. 网关返回 401

这表示认证没有通过。检查 JWT 是否来自登录接口、是否过期，随后重新执行携带有效 token 的请求。基础追踪实验可以先直接访问 `9102/orders/100`。

### 4. 订单接口成功，子调用却带错误

对照响应中的用户信息和 fallback 日志。降级将内部失败转成了可返回的业务结果，所以需要同时看外层响应、内部调用和降级原因。

## 十、本轮实测记录

联调使用临时端口和固定上游地址，避免混入已有 Eureka 实例；课堂启动步骤继续使用 Eureka 服务发现。网关验证使用与现有校验规则一致的测试 JWT，没有启动或改动认证数据库。

| 验证项 | 结果 |
| --- | --- |
| 三个模块构建及现有测试 | 全部通过 |
| 直接查询 Order → User | HTTP 200，返回真实用户；两个服务日志 trace ID 一致，Zipkin 收到 4 个 span |
| 有效 JWT 经过 Gateway → Order → User | HTTP 200；`X-Trace-Id` 与三个服务日志一致，Zipkin 收到 11 个 span，父子关系连通 |
| 停止本次用户服务后查询订单 | 返回降级用户 `id=0`；外层 200，熔断器记录连接失败及 fallback |
| 用户服务普通模式 | 仍能从原 Config Server 导入用户名配置 |

4 和 11 是本轮结果，包含框架额外操作；理解链路时关注服务、trace ID 和父子关系。临时验证进程已停止，学习时按第五节重新启动。

## 十一、本课练习

1. 连续查询订单 `100` 和 `101`，在 Zipkin 中找到两条 trace，确认日志中的订单 ID 与用户 `1` 的调用关系。
2. 通过网关查询一次订单，用 `X-Trace-Id` 定位它，再找出三个服务中属于该请求的日志。
3. 停止自己的用户服务，观察一次降级请求；恢复服务后比较正常与失败链路。

本课要掌握的是：用 trace ID 串联一次请求，用 span 判断每段调用发生了什么，并把业务响应和内部执行结果一起看。

参考资料：

- [Spring Boot Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Micrometer Glossary](https://docs.micrometer.io/tracing/reference/glossary.html)
- [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)
- [Zipkin Quickstart](https://zipkin.io/pages/quickstart.html)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
