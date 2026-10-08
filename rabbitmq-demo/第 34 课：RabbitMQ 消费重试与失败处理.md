# 第 34 课：RabbitMQ 消费重试与失败处理

## 一、失败之后应该怎么办

第 33 课用 `basicReject(tag, false)` 将失败消息直接送进死信队列。本课处理另一种情况：下游接口暂时超时，稍后再试可能成功。

我们让消费者最多尝试 **3 次**：第一次失败后等待 500ms，第二次失败后等待 1000ms，第三次仍失败才进入死信队列。HTTP 请求中的 `failTimes` 用来模拟“前几次处理失败”，方便看到重试成功和重试耗尽两个结果。

| 情况 | 业务例子 | 处理思路 |
| --- | --- | --- |
| 暂时失败 | 邮件服务短暂超时、下游限流 | 有次数上限并带等待的重试 |
| 持续失败 | 下游一直不可用 | 重试耗尽后进入死信，排查后再处理 |
| 无效消息 | 必填字段缺失、数据格式错误 | 通常应直接拒绝，重复尝试不会修正内容 |

本课统一抛出模拟异常来学习重试流程。实际业务应按异常类型决定是否重试。

## 二、消息的完整路线

```text
POST /lesson/retry/messages
           ↓
lesson.retry.exchange，routing key = retry.created
           ↓
lesson.retry.queue
           ↓ 一次投递
消费者第一次调用
  ├─ 成功 → 容器 ACK
  └─ 失败 → 等待 500ms
             ↓
         第二次调用
           ├─ 成功 → 容器 ACK
           └─ 失败 → 等待 1000ms
                      ↓
                  第三次调用
                    ├─ 成功 → 容器 ACK
                    └─ 失败 → 拒绝，requeue=false
                               ↓
                       lesson.retry.dlx
                               ↓
                       lesson.retry.dlq
```

新增的对象都以 `lesson.retry` 开头，原有的 `lesson.task.*` 拓扑保留。`RetryTopology` 声明新的主队列、死信交换机和死信队列。

这里的重试由 Java 消费容器执行：同一次投递的消息被再次交给监听方法。两次等待期间，消息仍然是 **Unacked**，消费线程也在等待。RabbitMQ 没有把它重新排进主队列。

## 三、关键代码

### 1. 单独配置重试容器

打开 `RetryListenerConfiguration.java`：

```java
factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
factory.setDefaultRequeueRejected(false);
factory.setAdviceChain(buildInterceptor());
```

`RetryTaskListener` 指定 `containerFactory = "retryListenerContainerFactory"`，所以这组设置只用于本课监听器。第 33 课继续使用手动 ACK。

`AUTO` 表示 Spring 容器在监听方法成功返回后发送 ACK；失败则按异常和恢复策略处理。它与 `NONE` 不同：`NONE` 不等待业务成功就视为交付，不能提供本课的成功后确认语义。

### 2. 设置次数和等待时间

```java
RetryInterceptorBuilder.stateless()
        .maxRetries(MAX_ATTEMPTS - 1)
        .backOffOptions(500, 2.0, 1000)
        .recoverer(new RejectAndDontRequeueRecoverer())
        .build();
```

当前项目使用 Spring AMQP 4.1.1，API 是 `maxRetries`：它计算首次调用之后允许重试几次。因此 `MAX_ATTEMPTS = 3` 对应 `maxRetries(2)`，即 **首次 1 次 + 重试 2 次 = 总计 3 次**。

`backOffOptions` 的三个参数分别是初始等待毫秒数、增长倍数和最大等待毫秒数。本课有两次等待：500ms、1000ms。处理调用本身耗时另算。

次数耗尽后，`RejectAndDontRequeueRecoverer` 让容器拒绝消息且不重新入队；主队列的 DLX 绑定再将消息送入 `lesson.retry.dlq`。若恢复器只吞掉异常并正常返回，容器可能 ACK，消息就不会走这个死信流程。[Spring AMQP 重试 API](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/rabbit/config/RetryInterceptorBuilder.html)、[错误恢复机制](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html)。

### 3. 用异常告诉容器“这次失败了”

`RetryTaskListener.handle` 记录本次尝试，当 `attempts <= failTimes` 时抛出 `IllegalStateException`；超过模拟失败次数后记录成功并返回。

如果把异常捕获后仅打印日志，再正常返回，容器会认为监听方法成功，本课的重试就不会发生。

## 四、启动应用

先停止自己正在运行的旧版 `rabbitmq-demo`，再在该模块目录启动更新后的应用：

```bash
bash ./run-demo.sh
```

脚本仍读取本模块的 `.rabbitmq.env.local`，连接 `121.4.77.117:5672`。应用地址为 `http://127.0.0.1:9140`；管理页面是 [http://121.4.77.117:15670/](http://121.4.77.117:15670/)。

同一个队列上的多个消费者会竞争消息。观察本课的 `/lesson/retry/messages` 时，保持一份本课消费者运行，便于把消息 ID 和进度对应起来。

## 五、实验一：第一次就成功

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/retry/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"直接发送邮件","failTimes":0}'
```

发送接口返回 `202`、`routed: true`，表示发布已获确认并成功路由。随后查看消费进度：

```bash
curl http://127.0.0.1:9140/lesson/retry/messages
```

找到发送响应中的相同 `id`，预期 `attempts: 1`、`status: "SUCCEEDED"`。

也可查询单条：`GET /lesson/retry/messages/{id}`。如果消息刚发布、消费者尚未记录它，这个接口可能暂时返回 `404`，稍后再次查询即可。

## 六、实验二：失败两次，第三次成功

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/retry/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"邮件服务短暂超时","failTimes":2}'
```

等待约 1.5 秒以上，再查看进度列表。该消息的结果应为：

```json
{
  "id": "发送接口返回的消息ID",
  "text": "邮件服务短暂超时",
  "failTimes": 2,
  "attempts": 3,
  "status": "SUCCEEDED"
}
```

日志中能看到这个 ID 的第 1、2、3 次处理。成功后容器 ACK，主队列恢复为空，这条消息不会进入死信队列。

`202` 返回时，重试可能还在进行。因此发布接口的响应与消费最终结果要分别观察。

## 七、实验三：三次都失败

```bash
curl -i -X POST http://127.0.0.1:9140/lesson/retry/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"邮件服务持续不可用","failTimes":3}'
```

等待后查看进度：预期 `attempts: 3`、`status: "EXHAUSTED"`。这个状态说明监听方法已尝试三次并失败；死信是否真正到达，还需要到管理页面检查。

进入 `lesson.retry.dlq`，查看相同消息 ID，并观察 `x-death` 头：

| 字段 | 本次实验预期 |
| --- | --- |
| `reason` | `rejected` |
| `queue` | `lesson.retry.queue` |
| `count` | 第一次死信时为 `1` |

三次业务调用都发生在同一次投递内，耗尽后只进行了一次死信转移，因此 `x-death.count` 为 `1`。`x-death` 记录死信历史，业务方法尝试次数需要另外记录。[RabbitMQ 死信机制](https://www.rabbitmq.com/docs/dlx)。

通过管理页面取消息时选择重新入队，便于保留实验结果。

## 八、重试与重新入队的区别

| 方式 | 谁执行 | 等待与上限 |
| --- | --- | --- |
| 本课容器重试 | Java 消费容器再次调用方法 | 本课固定两次等待，最多三次调用 |
| `basicReject(tag, true)` | RabbitMQ 将消息重新入队 | 这个调用本身不指定等待或次数上限 |
| TTL 延迟队列 | RabbitMQ 暂存消息，过期后再路由 | 可用于更长等待，后续课程学习 |

遇到持续错误就反复 `requeue=true`，可能形成快速的重复投递循环。消费线程内的短重试适合等待较短的场景；大量消息需要等待几分钟时，更适合用消息队列承接等待时间。

本课 `prefetch: 1` 且只有一个重试消费者，等待中的消息会占住这个消费者，后面的消息需要排队。

## 九、幂等和次数上限的边界

`RetryTaskProgress` 在内存中保存尝试次数与状态。已成功的相同消息 ID 再次交给监听器时直接返回，用来演示避免重复业务处理。

需要记住三个边界：

1. **三次上限针对一次投递中的重试流程。** 连接断开、进程重启或人工重投递后，会启动新的重试流程。
2. 进度和成功标记只保存在当前进程中，重启后会丢失。当前演示计数按 ID 累计；若把已耗尽的同一 ID 再次送回主队列，可能出现第 4 次处理。不要将它用作持久化的全局重试次数。
3. 每次 HTTP POST 都会生成新 ID，连续提交两次会产生两条不同消息。防止业务重复，需要订单号等稳定业务标识，并在数据库中保证幂等。

真实业务还要考虑“外部操作成功，但本地记录或 ACK 前崩溃”的情况。邮件发送、扣款等副作用的幂等规则，需要与相应业务系统配合设计。

## 十、练习与下一课

1. 分别发送 `failTimes` 为 0、1、2、3 的消息，记录各自尝试次数和最终状态。
2. 解释 `AUTO` 为什么仍然会在成功后发送 ACK。
3. 解释为什么重试耗尽的消息仍然先获得 HTTP `202`。
4. 解释为什么三次失败对应一次死信，而不是 `x-death.count: 3`。

练习结束后停止自己的 Java 进程即可，保留远程 RabbitMQ 和课程队列。

### 2026-10-08 实测结果

已在远程 `121.4.77.117:5672` 完成三条消息的验证：

| `failTimes` | 方法调用次数 | 最终状态 | RabbitMQ 结果 |
| --- | --- | --- | --- |
| `0` | `1` | `SUCCEEDED` | 成功 ACK |
| `2` | `3` | `SUCCEEDED` | 成功 ACK，没有进入死信 |
| `3` | `3` | `EXHAUSTED` | 同一消息 ID 进入 `lesson.retry.dlq`，`reason: rejected`、`count: 1` |

主队列最终 Ready / Unacked 均为 `0`。死信检查采用重新入队，保留一条“第34课：三次失败进入死信”消息供查看。验证使用临时端口 `19140`，并设置 `--lesson.task.listener-enabled=false`，使测试实例只消费本课重试队列。原有第 33 课消费者继续运行。

编译打包及全部 10 个测试通过；新增测试直接执行实际重试拦截器，覆盖第三次成功、次数耗尽与成功消息重复投递。

下一课可以用 TTL 与死信路由实现延迟处理，再把它与本课的短重试进行比较。
