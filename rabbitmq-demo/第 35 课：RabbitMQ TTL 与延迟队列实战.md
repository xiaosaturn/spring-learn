# 第 35 课：RabbitMQ TTL 与延迟队列实战

## 一、让任务晚一点执行

第 34 课的短重试由 Java 消费容器等待。本课把等待交给 RabbitMQ：消息先在没有消费者的队列中停留，过期后转发到另一个队列，再由消费者执行业务。

我们在 `rabbitmq-demo` 中模拟订单超时检查：创建订单后发送一条检查消息，等待队列的 TTL 为 **5000ms**。检查消息到达时，消费者读取订单最新状态：未支付则取消，已支付则保留。

支付接口只是修改内存中的模拟订单状态，不涉及实际付款。

## 二、生产到消费的完整路线

```text
POST /lesson/delay/orders
           ↓
DelayOrderController 创建内存订单，状态 WAITING_PAYMENT
           ↓
TaskPublisher.publishDelay 发送 {"orderId":"订单ID"}
           ↓ exchange=lesson.delay.exchange，key=delay.wait
lesson.delay.wait.queue
  · 没有消费者
  · x-message-ttl = 5000
  · 消息进入队列后开始计时
           ↓ 到期，成为 reason=expired 的死信
lesson.delay.ready.exchange（等待队列的 DLX）
           ↓ key=delay.ready
lesson.delay.ready.queue
           ↓
DelayOrderListener 查询当前订单状态
  ├─ WAITING_PAYMENT → CANCELLED
  └─ PAID → 保持 PAID
           ↓
监听方法返回，Spring 容器发送 ACK
```

死信也可以由过期产生。本课用过期后的转发实现延迟检查，这是预期的消息路线。

两个队列的职责不同：

| 队列 | 职责 | 有消费者吗 | 有 TTL 吗 |
| --- | --- | --- | --- |
| `lesson.delay.wait.queue` | 暂存等待中的消息 | 没有 | 5000ms |
| `lesson.delay.ready.queue` | 把到期消息交给业务处理 | 有 | 没有 |

如果给等待队列注册消费者，消息可能在到期前就被取走，延迟效果也就消失了。

## 三、TTL 的含义

TTL 是 Time To Live，表示消息在队列中的有效期。过期消息不会继续交付给该队列的普通消费者；配置了 DLX 时，会走死信路由。[RabbitMQ TTL](https://www.rabbitmq.com/docs/ttl)。

| 设置 | 作用 |
| --- | --- |
| 队列 `x-message-ttl` | 为进入这个队列的每条消息设置有效期 |
| 消息 `expiration` | 为某条消息设置有效期，值是毫秒数字字符串 |
| 队列 `x-expires` | 删除长时间未使用的队列 |

本课选固定的队列消息 TTL。每条消息从它进入等待队列开始计算 5 秒；这与队列创建时间无关。

如果同时设置队列消息 TTL 和消息自己的 `expiration`，会使用较小的值。

单队列中给每条消息设置不同 TTL 时，较短 TTL 的消息可能排在较长 TTL 的消息后面，死信转发会受队头位置影响。本课统一为 5 秒，便于观察。

## 四、关键代码

### 1. 声明等待队列

打开 `DelayTopology.java`：

```java
QueueBuilder.durable(WAIT_QUEUE)
        .ttl(DELAY_MS)
        .deadLetterExchange(READY_EXCHANGE)
        .deadLetterRoutingKey(READY_KEY)
        .build();
```

`DELAY_MS = 5000`。`.ttl()` 对应 `x-message-ttl`，`.expires()` 则对应队列删除期限，两个 API 的用途不同。

等待队列不会被任何本课监听器订阅。就绪队列正常声明为 durable 队列，不设置消息 TTL。

### 2. 生产者发送订单 ID

`DelayOrderController` 先创建待支付订单，再调用：

```java
publisher.publishDelay(new DelayOrderMessage(order.id()));
```

生产者复用此前的 JSON 转换、持久化标记、发布确认和 mandatory return 检查。创建接口在检查消息发布成功并路由到等待队列后返回 HTTP `202`。

消息中只带订单 ID；订单是否已经支付，由消费者到期时查询。这样可以使用检查发生时的最新业务状态。

### 3. 消费者订阅就绪队列

`DelayOrderListener` 使用：

```java
@RabbitListener(queues = DelayTopology.READY_QUEUE, ackMode = "AUTO")
```

监听器读取 RabbitMQ 添加的 `x-death` 头，再调用 `orders.checkTimeout(...)`。本课将死信原因和次数保存到查询结果中，便于把消息路径与业务结果对应起来。

`DelayOrderStore` 用原子状态更新处理支付与超时检查：支付先成功，后续检查保留 `PAID`；取消先成功，后续支付请求返回 `409`。相同订单的重复检查保留第一次检查结果。

## 五、启动应用

停止自己正在运行的旧版 `rabbitmq-demo`，再进入该模块目录运行：

```bash
bash ./run-demo.sh
```

脚本读取本模块 `.rabbitmq.env.local`，继续使用远程 RabbitMQ。应用地址为 `http://127.0.0.1:9140`，管理页面为 [http://121.4.77.117:15670/](http://121.4.77.117:15670/)。

保持一份本课消费者运行。订单存储在该应用进程的内存中；多份应用竞争消费，会使拿到消息的进程不一定拥有对应订单。

## 六、实验一：不支付，等待取消

创建订单：

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/delay/orders \
  -H 'Content-Type: application/json' \
  -d '{"product":"Spring Cloud 学习资料"}'
```

响应为 `202`，订单包含 `id`、`status: "WAITING_PAYMENT"` 和 `createdAt`。`checkedAt` 此时为空，说明检查还未发生。

立即查询所有订单：

```bash
curl http://127.0.0.1:9140/lesson/delay/orders
```

约 5 秒以后再查，找到相同订单 ID，预期：

```json
{
  "status": "CANCELLED",
  "deathReason": "expired",
  "deathCount": 1
}
```

实际响应还包含商品、创建时间和检查时间。也可用 `GET /lesson/delay/orders/{id}` 查询单条。

在管理页面观察等待队列：`Consumers` 应为 `0`；消息过期后转入就绪队列并被消费。消息数量统计有更新间隔，不一定能在页面中看到每次短暂变化。

## 七、实验二：先支付，检查时保留订单

创建一笔新订单，复制响应中的 `id`，尽快调用模拟支付接口：

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/delay/orders/替换为订单ID/pay
```

支付成功后状态为 `PAID`，`paidAt` 有值。

等待检查完成，再查询该订单：它仍应为 `PAID`，同时 `checkedAt` 有值，`deathReason: "expired"`、`deathCount: 1`。

这说明延迟消息仍然到达并被处理，只是消费者根据最新状态决定保留订单。支付成功不会自动删除等待队列中原来的检查消息。

## 八、5 秒与业务截止时间的区别

TTL 从消息进入等待队列算起。过期转发、队列排队、网络传输和业务处理都需要时间，因此检查可能晚于 5 秒。这个机制提供延迟处理，无法保证恰好在第 5 秒执行。

本课的支付接口依据订单当前状态判断能否支付；若消费者尚未取消订单，即使创建已经超过 5 秒，支付仍可能成功。

如果业务规定“超过截止时间一定不能支付”，应保存明确的业务截止时间，并让支付接口与取消逻辑都检查它。队列延迟用于触发检查，业务规则仍由应用决定。

## 九、与上一课比较

| 内容 | 第 34 课容器内重试 | 第 35 课 TTL 延迟处理 |
| --- | --- | --- |
| 等待发生在哪里 | Java 消费线程 | RabbitMQ 等待队列 |
| 等待时消息状态 | 已交付，尚未确认 | 等待队列中未被消费 |
| 等待期间业务消费者 | 持有本次消息，等待后再调用方法 | 尚未收到该消息 |
| 触发条件 | 监听方法抛异常 | 消息 TTL 到期 |
| 死信原因 | 次数耗尽后 `rejected` | `expired` |

本课可以用来理解订单超时检查、稍后发送提醒、延迟重试等场景。更长的固定等待时间可以使用对应的队列消息 TTL。

## 十、演示的边界

1. **订单只在内存中。** 重启应用后订单会丢失，而 RabbitMQ 中的持久消息可能仍存在。缺少订单时，本课监听器记录日志并返回；生产环境应查询数据库中的订单。
2. **创建订单与发送消息尚未形成事务。** 发布失败时可能留下待支付订单；确认超时也不代表消息一定没有到达。实际业务需要处理这个一致性问题，例如事务 Outbox 与对账补偿。
3. **发布确认覆盖最初发送。** 默认死信转发自身没有发布确认，目标不可用时可能丢失消息。更强的保证需要配置支持至少一次死信转发的 quorum 队列，并结合补偿机制。[RabbitMQ 死信转发安全性](https://www.rabbitmq.com/docs/dlx)。
4. **相同名字的队列参数需要保持一致。** 直接修改 `DELAY_MS` 后声明旧等待队列，会产生参数冲突。其他 TTL 实验应使用新的完整拓扑命名空间，例如 `lesson.delay10.*`；实际部署通常通过 policy 管理可调整配置。

## 十一、练习

1. 创建一个订单立即查询，再在检查完成后查询，比较状态与时间字段。
2. 创建两个订单，只支付其中一个，观察检查完成后的不同结果。
3. 比较 `deathReason: expired` 与上一课的 `rejected` 各表示什么。
4. 检查等待队列 `Consumers` 为 `0`，解释消息仍能转入就绪队列的原因。

练习结束后停止自己的应用即可，远程 RabbitMQ 和课程拓扑保留。

### 2026-10-08 实测结果

在远程 `121.4.77.117:5672` 完成两笔模拟订单验证：

| 实验 | 检查前 | 检查后 | 从创建到检查 |
| --- | --- | --- | --- |
| 未支付 | `WAITING_PAYMENT` | `CANCELLED` | 约 5.23 秒 |
| 已支付 | `PAID` | `PAID`，有 `checkedAt` | 约 5.02 秒 |

两笔订单记录的死信原因均为 `expired`、次数为 `1`。取消后再模拟支付返回 HTTP `409`。等待队列的 TTL 为 `5000`，消费者数量为 `0`；就绪队列无 TTL，验证时消费者数量为 `1`。

测试实例使用临时端口 `19150`，只启用本课消费者，原有课程消费者数量保持不变。编译打包及全部 14 个测试通过，新增测试覆盖未支付取消、已支付保留、取消后拒绝支付，以及死信头解析与重复检查。
