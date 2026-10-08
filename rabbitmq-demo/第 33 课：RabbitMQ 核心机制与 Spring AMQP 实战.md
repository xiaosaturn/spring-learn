# 第 33 课：RabbitMQ 核心机制与 Spring AMQP 实战

## 一、本课要弄清什么

第 32 课借助 Spring Cloud Bus 使用 RabbitMQ 传播配置刷新事件。本课暂时不经过 Bus，直接使用 Spring AMQP 发送和消费业务消息，观察 RabbitMQ 自己负责的路由、队列、确认与失败处理。

本课新增独立的 `rabbitmq-demo`，不会改变现有订单接口。运行它只需要 RabbitMQ，不需要 Eureka 或 Config Server。

## 二、一条消息经过哪里

```text
HTTP 请求
   ↓
TaskPublisher 生产者
   ↓ exchange=lesson.task.exchange, routingKey=task.created
Direct Exchange
   ↓ binding: task.created
主队列 lesson.task.queue
   ↓
TaskListener 消费者
   ├─ 成功：记录结果，然后 basicAck
   └─ 模拟失败：basicReject(requeue=false)
                        ↓
                 lesson.task.dlx
                        ↓
                 lesson.task.dlq 死信队列
```

生产者把消息发给交换机，而不是直接发给消费者。交换机按绑定规则把消息路由到队列；消费者从队列取得消息。

| 名称 | 作用 | 本课实例 |
| --- | --- | --- |
| Exchange | 接收生产者的消息并决定路由 | `lesson.task.exchange` |
| Queue | 保存等待消费的消息 | `lesson.task.queue` |
| Binding | 把交换机和队列连接起来 | `task.created` |
| Routing key | 生产者给出的路由标记 | `task.created` |
| Consumer | 处理队列交付的消息 | `TaskListener` |
| DLX / DLQ | 接住被拒绝且不重新入队的消息 | `lesson.task.dlx` / `lesson.task.dlq` |

常见交换机类型：

| 类型 | 匹配规则 | 例子 |
| --- | --- | --- |
| Direct | Routing key 与 binding key 完全相同 | `task.created` |
| Fanout | 广播到所有绑定的队列，忽略 routing key | 同一事件通知多个服务 |
| Topic | 使用 `*` 和 `#` 匹配 routing key 的分段 | `order.*`、`order.#` |
| Headers | 根据消息头匹配 | 按多个属性选择队列 |

本课选 Direct，因为它最容易观察“匹配”和“不匹配”的区别。

再区分三个容易混淆的层次：应用通过 TCP **Connection** 连接 Broker，在连接上使用 **Channel** 执行发布、消费和确认；**Virtual host** 把交换机、队列和权限隔离成不同空间。本课使用 RabbitMQ 默认的 `/` virtual host。`prefetch: 1` 限制这个示例的消费者一次只持有一条未确认消息，便于观察 Ready 和 Unacked 数量。

## 三、启动并观察拓扑

本课使用你提供的远程 RabbitMQ。两个端口的用途不同：

| 用途 | 地址 | 谁来连接 |
| --- | --- | --- |
| 管理页面（HTTP） | [http://121.4.77.117:15670/](http://121.4.77.117:15670/) | 浏览器查看队列、交换机和消息 |
| 消息连接（AMQP） | `121.4.77.117:5672` | Java 生产者与消费者 |

账号为 `rabbitmq`，virtual host 为 `/`。Java 应用连接 `5672`，不能把管理页面的 `15670` 填成消息端口。

连接信息保存在 `rabbitmq-demo/.rabbitmq.env.local`，通过 Spring Boot 的 `SPRING_RABBITMQ_*` 环境变量覆盖 `application.yml`。当前电脑已经配置好该文件；它被本模块的 `.gitignore` 忽略，密码不会写进课程代码。换一台电脑时，在 `rabbitmq-demo` 目录将 `.rabbitmq.env.example` 复制为 `.rabbitmq.env.local` 并填写自己的密码。

在 `rabbitmq-demo` 目录运行：

```bash
bash ./run-demo.sh
```

`run-demo.sh` 会加载上述本地配置，再启动应用。服务地址为 `http://127.0.0.1:9140`。`RabbitTopology` 中的 Bean 会声明两个 durable 交换机、两个 durable 队列及相应绑定。打开管理页面的 **Exchanges**、**Queues and Streams** 页面，找到名字以 `lesson.task` 开头的对象。

远程服务器可能被多个应用共用。本课启动前已确认 `/` 空间中没有同名对象，只创建了 `lesson.task.*`。后续练习不要清空其他队列；同时运行两份本课应用时，它们会竞争消费同一个主队列，因此一条消息只会出现在其中一份应用的 `/processed` 中。

### 可选：使用本机 RabbitMQ

如果改用本机，在 `config-server` 目录启动上一课准备的 RabbitMQ：

```bash
docker compose -f compose.rabbitmq.yml up -d --wait
```

首次运行需要下载镜像。容器健康后，AMQP 端口是本机 `5672`，管理页面是 [http://127.0.0.1:15672](http://127.0.0.1:15672)，本地示例账号和密码都是 `guest`。

随后在没有加载远程连接环境变量的终端中，到 `rabbitmq-demo` 目录运行：

```bash
bash ./mvnw spring-boot:run
```

这个命令使用 `application.yml` 中的本机默认值；远程方案则使用 `run-demo.sh`。

`durable` 表示交换机或队列定义在 Broker 重启后仍可存在；消息本身还设置了 `PERSISTENT`。这些设置与发布确认配合，才构成发送端的持久化基础。它们不代表消费者已经处理完成。

## 四、发送一条成功消息

在另一个终端运行：

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"发送欢迎邮件","fail":false}'
```

预期 HTTP 状态是 `202 Accepted`，响应含有 `id`、`routingKey: "task.created"`、`routed: true`。这里的 `id` 是本课用来识别消息的唯一值。

随后查看消费者的处理结果：

```bash
curl http://127.0.0.1:9140/lesson/messages/processed
```

消费者收到 JSON 消息后先记录结果，再调用 `basicAck`。若你在管理页面看到主队列消息数很快回到 0，这是因为消息已被消费并确认。

### 为什么发送接口返回 202

`TaskPublisher` 等待 RabbitMQ 的 publisher confirm，并检查 mandatory return。`routed: true` 表示 Broker 确认发布，且消息没有因找不到队列而退回。它并不保证消费者的业务逻辑已经完成；消费是后续独立的一步。

为了让练习的 HTTP 响应直接显示发布结果，这里最多等待确认 5 秒。高吞吐量场景通常会异步处理发布确认。

## 五、验证 routing key 不匹配

把查询参数改成未绑定的路由键：

```bash
curl -i -X POST 'http://127.0.0.1:9140/lesson/messages?routingKey=task.unknown' \
  -H 'Content-Type: application/json' \
  -d '{"text":"找不到队列","fail":false}'
```

预期 HTTP 状态为 `422 Unprocessable Entity`，响应中 `routed: false`。RabbitMQ 可以确认自己收到了这次发布，但交换机没有匹配的队列，因此通过 mandatory return 把消息退回。这说明 **publisher confirm 与成功路由是两件事**。

## 六、验证消费者确认与死信

发送一条故意失败的消息：

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"模拟处理失败","fail":true}'
```

发送端仍可收到 `202`，因为消息成功到达主队列。`TaskListener` 根据 `fail` 标记调用 `basicReject(deliveryTag, false)`：第二个参数 `false` 表示不重新放回主队列。主队列配置了死信交换机，RabbitMQ 因而把这条消息路由到 `lesson.task.dlq`。

在管理页面进入 `lesson.task.dlq`，查看消息数量；也可在 **Get messages** 中取出消息，观察消息体与 `x-death` 头。取出操作本身可能改变队列状态，检查时留意是否选择重新入队。这里没有为死信队列配置消费者，因此失败消息会留在队列中供检查。

本课为便于自包含演示，在队列声明时使用 `x-dead-letter-exchange` 等参数。RabbitMQ 官方文档更建议实际部署通过 policy 配置 DLX，因为队列参数改变后通常需要重建队列。

## 七、三种确认分别回答什么问题

| 机制 | 发出方 | 回答的问题 |
| --- | --- | --- |
| Publisher confirm | RabbitMQ → 生产者 | Broker 是否确认这次发布？ |
| Mandatory return | RabbitMQ → 生产者 | 消息是否因无匹配队列被退回？ |
| Consumer ack / reject | 消费者 → RabbitMQ | 交付的消息是否已处理，或应被拒绝？ |

可靠性不能只靠其中一个开关。本课用手动 ack 表达“处理成功后再确认”；如果消费者在记录结果后、ack 前断开，消息可能重新投递。`ProcessedTasks` 按消息 `id` 去重，帮助观察重复投递，但它只存在内存中。真实业务通常需要在持久化存储中按消息 ID 做幂等处理。

## 八、练习与清理

1. 多发几条成功消息，比较 HTTP 发布响应和 `/processed` 内容。
2. 发送一条 `fail=true` 的消息，在管理页面检查主队列与死信队列。
3. 用 `routingKey=task.unknown` 重试，解释为什么 Broker 确认了发布，接口却返回 `routed: false`。
4. 在运行应用的终端按 `Ctrl+C` 停止 `rabbitmq-demo`。远程 RabbitMQ 保持运行，课程队列也保留，便于继续观察。

只有采用本机 Docker 方案时，才在 `config-server` 目录运行 `docker compose -f compose.rabbitmq.yml down`。当前 Compose 文件没有配置持久数据卷；重新创建容器时，不要依赖旧消息仍在。

### 2026-10-08 实测结果

在远程 `121.4.77.117:5672` 上已完成以下验证：

| 实验 | 实际结果 |
| --- | --- |
| 正常消息 | HTTP `202`、`routed: true`，相同消息 ID 出现在 `/processed` 中，主队列 Ready / Unacked 均为 `0` |
| `task.unknown` | HTTP `422`、`routed: false`，无匹配队列 |
| `fail: true` | HTTP `202`，相同消息 ID 出现在 `lesson.task.dlq`，`x-death.reason` 为 `rejected` |

死信检查采用了重新入队方式，保留一条“第33课：模拟失败进入死信”消息供你在管理页面查看。

## 九、常见问题

### 启动后反复出现 `Connection refused`

远程方案先检查 `.rabbitmq.env.local` 中的主机和消息端口，并通过 `bash ./run-demo.sh` 加载它们。管理页面可访问只说明 HTTP 端口可用，消息端口 `5672` 也必须能连接。若使用本机方案，则确认 RabbitMQ 容器健康。

### 日志显示连接 `127.0.0.1:5672`，而我想用远程服务器

说明当前启动方式没有加载远程环境变量。使用 `bash ./run-demo.sh`；若从 IDE 启动，则在运行配置中设置 `.rabbitmq.env.local` 中的 `SPRING_RABBITMQ_*` 变量。

### 启动时出现 `PRECONDITION_FAILED` 或队列参数冲突

RabbitMQ 不允许用同一个名字重新声明参数不同的队列。确认没有其他程序使用相同的 `lesson.task.*` 名称，并检查 virtual host。远程共享服务器上不要直接删除同名队列；先确认归属，必要时使用独立的学习 virtual host。

### 接口返回 202，但 `/processed` 为空

发布确认只覆盖发送阶段。查看 Java 服务的消费者日志、主队列的 Ready / Unacked 数量和死信队列；如果发送的是 `fail=true`，它本来就不会进入 `/processed`。

参考资料：

- [RabbitMQ AMQP 模型](https://www.rabbitmq.com/tutorials/amqp-concepts)
- [RabbitMQ 消费者确认与发布确认](https://www.rabbitmq.com/docs/confirms)
- [RabbitMQ 死信交换机](https://www.rabbitmq.com/docs/dlx)
- [Spring Boot AMQP](https://docs.spring.io/spring-boot/reference/messaging/amqp.html)
- [Spring AMQP Publisher Confirms and Returns](https://docs.spring.io/spring-amqp/reference/amqp/template.html)
