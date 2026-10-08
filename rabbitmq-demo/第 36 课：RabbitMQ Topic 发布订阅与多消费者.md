# 第 36 课：RabbitMQ Topic 发布订阅与多消费者

## 一、同一个订单事件通知多个业务

本课模拟三个订单事件：创建、付款和取消。通知业务关注所有这三种事件，积分业务只关注付款事件。

生产者向交换机发送一次事件，交换机按绑定规则把它路由到各业务自己的队列。两个业务分别消费、分别确认，处理速度可以不同。

本课新增 `lesson.topic.*` 拓扑。通知和积分处理暂时只是保存内存记录；订单事件由接口手动发布，用来观察路由和消费过程。

## 二、消息的路线

```text
POST /lesson/topic/events
           ↓
TaskPublisher.publishTopic
           ↓ exchange=lesson.topic.exchange，routing key=order.paid
Topic Exchange
   ├─ binding=order.*
   │      ↓
   │  lesson.topic.notification.queue
   │      ↓
   │  TopicNotificationListener → 记录通知处理结果 → ACK
   │
   └─ binding=order.paid
          ↓
      lesson.topic.points.queue
          ↓
      TopicPointsListener → 记录积分处理结果 → ACK
```

在这个路由例子中，付款事件同时匹配两个队列。两个消费者看到相同事件 ID，各自处理自己队列中的消息。

## 三、Topic 的匹配规则

Routing key 通常用点分成多段，例如 `order.paid` 和 `order.paid.refund`。Topic 交换机按 binding key 匹配这些分段：

| Binding key | 匹配内容 | 示例 |
| --- | --- | --- |
| `order.paid` | 精确匹配 | `order.paid` |
| `order.*` | `order` 后恰好一段 | `order.created`、`order.paid` |
| `order.#` | `order` 后零段或多段 | `order`、`order.paid`、`order.paid.refund` |

本课声明前两条绑定，第三条用于理解规则。`order.*` 不匹配 `order.paid.vip`，因为它在 `order` 后面有两段。[RabbitMQ Topic 教程](https://www.rabbitmq.com/tutorials/tutorial-five-spring-amqp)。

本课三个正常事件的路由结果：

| 事件类型 | Routing key | 通知队列 `order.*` | 积分队列 `order.paid` |
| --- | --- | --- | --- |
| `created` | `order.created` | 收到 | 无匹配 |
| `paid` | `order.paid` | 收到 | 收到 |
| `cancelled` | `order.cancelled` | 收到 | 无匹配 |

## 四、多个队列与多个消费者

队列是区分业务订阅的重要单位。

| 结构 | 消息如何分配 |
| --- | --- |
| 通知和积分各有一个队列 | 两个匹配队列分别接收一份事件 |
| 同一通知队列连接两份通知服务实例 | 实例共同分担该队列消息，一次投递交给其中一个消费者 |

因此，多个业务要分别收到同一事件，就为它们建立各自的队列。某个业务需要提高处理能力，则增加消费它同一个队列的实例。[RabbitMQ 消费者](https://www.rabbitmq.com/docs/consumers)。

这里的一份投递不等于全生命周期只处理一次。断线或确认前崩溃可能导致重新投递，各业务仍需要自己的幂等规则。

## 五、关键代码

### 1. 声明交换机和绑定

`TopicTopology` 声明 durable 的 Topic 交换机：

```java
new TopicExchange(EXCHANGE, true, false);
```

通知队列绑定为：

```java
BindingBuilder.bind(queue).to(exchange).with("order.*");
```

积分队列绑定为：

```java
BindingBuilder.bind(queue).to(exchange).with("order.paid");
```

交换机负责根据 routing key 选择队列，监听器负责处理已经进入自己队列的事件。

### 2. 生产者发送事件

`TopicEventController` 接收 `orderId` 和 `eventType`。事件类型为 `created`、`paid`、`cancelled`，默认路由键是 `order.` 加事件类型。

`TaskPublisher.publishTopic` 生成事件 UUID，构造：

```java
new TopicOrderEvent(id, orderId, eventType);
```

它复用已有的 JSON 转换、持久化标记、发布确认和 mandatory return 检查。

`orderId` 是业务订单标识，`id` 是本次消息事件的标识。同一个订单可以产生多个事件。

### 3. 两个监听器分别处理

`TopicNotificationListener` 监听通知队列，`TopicPointsListener` 监听积分队列，两者都使用 Spring `AUTO` 确认。

监听方法拿到 `TopicOrderEvent` 和消息实际收到的 routing key，分别写入 `TopicEventLog`。方法成功返回后，容器发送 ACK。

两份消费记录分别按事件 ID 去重。通知业务已处理某个 ID，不会因此阻止积分业务处理同一个 ID。

## 六、启动与发送

停止自己运行的旧版应用，然后在 `rabbitmq-demo` 目录运行：

```bash
bash ./run-demo.sh
```

应用继续通过本模块 `.rabbitmq.env.local` 连接远程 RabbitMQ。管理页面为 [http://121.4.77.117:15670/](http://121.4.77.117:15670/)。

先发一个创建事件：

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/topic/events \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1001","eventType":"created"}'
```

再发付款事件：

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/topic/events \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1001","eventType":"paid"}'
```

两个接口都应返回 HTTP `202`，响应中包含 `id`、`routingKey` 和 `routed: true`。等待消费后查询：

```bash
curl http://127.0.0.1:9140/lesson/topic/events
```

结果有两个列表：`notifications` 包含创建和付款事件，`points` 只包含付款事件。找到付款消息的 ID，它应分别出现在两个列表中。

这两个列表是当前 Java 进程的内存记录。也可以发送 `eventType: "cancelled"`，观察通知列表增加、积分列表保持原有内容。

## 七、观察一个不匹配的路由键

接口的 `routingKey` 查询参数供路由实验使用。覆盖成三段路由键：

```bash
curl -i -X POST 'http://127.0.0.1:9140/lesson/topic/events?routingKey=order.paid.vip' \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1002","eventType":"paid"}'
```

该路由键不匹配当前任意绑定，预期返回 HTTP `422`、`routed: false`。正常发送时由接口根据事件类型生成 routing key 即可。

确认成功且未退回，只能表明消息路由到了至少一个匹配队列。例如通知绑定仍在、积分绑定缺失时，付款事件仍可能返回 `202`，但积分队列收不到它。mandatory return 无法判断所有预期业务订阅是否完整。[RabbitMQ 发布确认](https://www.rabbitmq.com/docs/confirms)。

## 八、分开运行通知与积分消费者

这个实验让两个业务分别运行在两个 Java 进程中。先停止单实例应用，然后在 `rabbitmq-demo` 目录打包：

```bash
bash ./mvnw package
```

终端 A：只启用通知消费者。

```bash
set -a
source ./.rabbitmq.env.local
set +a

java -jar target/rabbitmq-demo-0.0.1-SNAPSHOT.jar \
  --server.port=19160 \
  --lesson.task.listener-enabled=false \
  --lesson.retry.listener-enabled=false \
  --lesson.delay.listener-enabled=false \
  --lesson.topic.points-enabled=false
```

等 A 启动成功，在 `http://127.0.0.1:19160/lesson/topic/events` 发布一条付款事件。A 启动时会声明两个队列及绑定，因此此时积分消费者虽然没有运行，消息仍可保存在它的队列中。

查看 A 的消费记录：通知列表出现该事件，积分列表为空。管理页面中 `lesson.topic.points.queue` 的消费者数量为 `0`，Ready 消息最终为 `1`；统计更新可能稍有延迟。

终端 B：随后只启动积分消费者。

```bash
set -a
source ./.rabbitmq.env.local
set +a

java -jar target/rabbitmq-demo-0.0.1-SNAPSHOT.jar \
  --server.port=19161 \
  --lesson.task.listener-enabled=false \
  --lesson.retry.listener-enabled=false \
  --lesson.delay.listener-enabled=false \
  --lesson.topic.notification-enabled=false
```

分别查询 A、B：

```bash
curl http://127.0.0.1:19160/lesson/topic/events
curl http://127.0.0.1:19161/lesson/topic/events
```

B 应能处理之前排队的同一付款事件。此时 A 的通知列表有记录，B 的积分列表有记录；各进程查询只显示自己的内存数据。

这个实验说明队列可以让不同业务以各自速度处理消息。前提是交换机、队列和绑定已经建立；RabbitMQ 不会为从未声明的业务订阅自动创建队列。

## 九、事件处理与业务一致性

本课人工发布模拟事件，并未与第 35 课的订单支付接口联动；通知和积分只是记录结果。

实际项目中，订单服务通常在业务状态变化后发布事件，通知服务和积分服务订阅各自队列。发布与订单事务的一致性，以及每个消费者的失败重试，都需要继续处理。

本课的 eventId 去重只在内存中，重启后会丢失。连续提交两次 HTTP 请求也会产生两个新事件 ID。实际积分业务可使用订单号和事件类型等稳定业务标识，并在数据库中保证重复事件不会重复记账。

不同队列的消费者分别确认消息。通知处理完成，积分处理可能仍在排队；生产者的发布响应不会等待所有消费者业务完成。

## 十、练习与总结

1. 发布创建、付款、取消三个事件，按事件 ID 对照两个消费列表。
2. 解释通知队列与积分队列为何能分别收到同一个付款事件。
3. 解释把两个业务消费者都放在同一个队列上会产生怎样的分配方式。
4. 用 `order.paid.vip` 验证 `order.*` 的匹配边界。
5. 完成双进程实验，观察积分消费者晚启动时如何消费已排队消息。

练习结束后停止自己启动的 Java 进程，课程交换机和队列保留。

### 2026-10-08 实测结果

已使用远程 `121.4.77.117:5672` 和两个临时 Java 进程完成验证：通知实例 A 在 `19160`，积分实例 B 在 `19161`。

| 实验 | 通知实例 A | 积分实例 B | 发布结果 |
| --- | --- | --- | --- |
| B 尚未启动时发 `order.paid` | 已处理 | 队列保存 1 条 Ready 消息，消费者为 0 | HTTP `202` |
| 随后启动 B | 保留通知记录 | 处理此前同一事件 ID | 消息成功消费 |
| 发 `order.created` | 已处理 | 无对应记录 | HTTP `202` |
| 发 `order.cancelled` | 已处理 | 无对应记录 | HTTP `202` |
| 发 `order.paid.vip` | 无对应记录 | 无对应记录 | HTTP `422`、`routed: false` |

A 最终有 3 条通知记录，B 有 1 条积分记录；原有课程消费者数量保持不变。编译打包和全部 17 个测试通过，新增测试覆盖两个业务独立处理、重复投递去重，以及生产者的事件与消息属性。
