# 第 32 课：Spring Cloud Bus 多实例配置刷新

## 一、本课目标

第 31 课的 `/actuator/refresh` 只刷新收到请求的那一个 `user-service` 实例。如果同一个服务运行两份，逐个调用容易遗漏。本课让两个实例通过 Spring Cloud Bus 接收同一次刷新事件。

```text
修改 config-repo/user-service.yml
                ↓
向实例 A 发送 POST /actuator/busrefresh
                ↓
             RabbitMQ
             ↙      ↘
       实例 A 刷新   实例 B 刷新
             ↘      ↙
      两个实例再次读取 Config Server
```

Config Server 仍只负责提供配置。本课由一个客户端发起 Bus 事件；RabbitMQ 负责把事件送给其他客户端。

## 二、代码变化

`user-service/pom.xml` 加入 `spring-cloud-starter-bus-amqp`。这个 starter 使用 RabbitMQ 作为消息代理。上一课的 `@RefreshScope` 保留，因此控制器在刷新后可以取得新的 `lesson.user-name`。

`user-service/src/main/resources/application.yml` 的管理端点增加 `busrefresh`：

```yaml
management:
  server:
    address: 127.0.0.1
    port: 9104
  endpoints:
    web:
      exposure:
        include: health,refresh,busrefresh
```

`/actuator/refresh` 用来刷新当前实例；`/actuator/busrefresh` 向 Bus 发布刷新事件。管理端口继续仅绑定本机，避免把刷新能力直接暴露到业务网络。

普通上下文测试不需要连接消息代理，因此 `user-service/src/test/resources/application.yml` 设置了 `spring.cloud.bus.enabled: false`。运行本课的两个服务实例时仍会启用 Bus。

## 三、准备 RabbitMQ

本课使用远程 RabbitMQ：

| 配置项 | 值 |
| --- | --- |
| AMQP 消息连接 | `121.4.77.117:5672` |
| 管理页面 | [http://121.4.77.117:15670/](http://121.4.77.117:15670/) |
| 用户名 | `rabbitmq` |
| Virtual host | `/` |

`15670` 是管理页面的 HTTP 端口，Java 客户端发送和接收消息使用 `5672`。二者用途不同，不能把管理页面的端口填进 RabbitMQ 连接配置。

`rabbitmq-demo/.rabbitmq.env.example` 是连接配置模板，`rabbitmq-demo/.rabbitmq.env.local` 保存本机使用的实际配置，并已被 Git 忽略。若本地文件尚不存在，在仓库根目录复制模板：

```bash
cp rabbitmq-demo/.rabbitmq.env.example rabbitmq-demo/.rabbitmq.env.local
```

在本地文件中填写密码。连接配置使用 Spring Boot 的标准环境变量：`SPRING_RABBITMQ_HOST`、`SPRING_RABBITMQ_PORT`、`SPRING_RABBITMQ_USERNAME`、`SPRING_RABBITMQ_PASSWORD` 和 `SPRING_RABBITMQ_VIRTUAL_HOST`。密码只放在本地文件，不写进课程文档或提交到 Git。

在 `config-server` 目录启动 Config Server：

```bash
bash ./mvnw spring-boot:run
```

## 四、启动两个客户端实例

先在 `user-service` 目录打包：

```bash
bash ./mvnw package
```

然后在两个终端分别进入 `user-service` 目录，加载连接环境变量并启动实例。`set -a` 会让文件中的变量成为 Java 进程可以读取的环境变量，`set +a` 在加载后关闭这个选项。

终端 A：

```bash
set -a
source ../rabbitmq-demo/.rabbitmq.env.local
set +a

java -jar target/user-service-0.0.1-SNAPSHOT.jar \
  --server.port=9102 \
  --management.server.port=9105 \
  --spring.application.index=1 \
  --spring.cloud.bus.destination=spring-learn-bus \
  --eureka.client.enabled=false
```

终端 B：

```bash
set -a
source ../rabbitmq-demo/.rabbitmq.env.local
set +a

java -jar target/user-service-0.0.1-SNAPSHOT.jar \
  --server.port=9103 \
  --management.server.port=9106 \
  --spring.application.index=2 \
  --spring.cloud.bus.destination=spring-learn-bus \
  --eureka.client.enabled=false
```

两个业务端口和管理端口各不相同。`spring.application.index` 也各不相同，让两个实例拥有不同的 Bus ID；否则事件可能被误判为来自同一个实例。这次实验直接访问用户服务，因此关闭 Eureka 客户端，减少无关的启动依赖。

两个实例都设置 `spring.cloud.bus.destination=spring-learn-bus`，让本课的配置刷新事件使用同一个专用消息目的地。远程服务器上可能还运行其他应用；使用课程自己的目的地可以把这些事件与其他应用使用的 Bus 目的地分开。需要互相刷新配置的实例必须使用相同的目的地。

## 五、观察一次广播刷新

先查询两个实例，确认它们返回相同的当前名字：

```bash
curl http://localhost:9102/users/1
curl http://localhost:9103/users/1
```

修改 `config-server/config-repo/user-service.yml` 中的 `lesson.user-name`。Config Server 会读到新值，但两个已运行实例仍返回旧名字。

只向实例 A 的管理端口发送一次请求：

```bash
curl -X POST http://127.0.0.1:9105/actuator/busrefresh
```

消息传播和各实例重新读取配置需要一点时间。再次查询两个实例：

```bash
curl http://localhost:9102/users/1
curl http://localhost:9103/users/1
```

两个名字都应变成新值。如果只有一个实例改变，先检查两个实例是否连接到同一个 RabbitMQ 和 virtual host、是否都使用 `spring-learn-bus`，再检查它们的 Bus ID 是否不同。

如果启动日志反复出现 `Connection refused`，先检查是否加载了 `.rabbitmq.env.local`，以及远程 AMQP 端口 `5672` 是否可连接。管理页面可以打开，并不代表消息端口一定可连接。若提示认证或权限错误，检查账号、密码，以及账号在 `/` virtual host 中的权限，再发送刷新请求。

与第 31 课比较：若改用 `POST http://127.0.0.1:9105/actuator/refresh`，只会刷新实例 A；实例 B 不会因这次请求而更新。

### 2026-10-08 实测结果

已用远程 `121.4.77.117:5672` 和课程目的地 `spring-learn-bus` 完成双实例验证。测试采用独立的临时配置目录及端口，结果如下：

| 阶段 | 实例 A | 实例 B |
| --- | --- | --- |
| 启动时 | `Bus远程验证-刷新前` | `Bus远程验证-刷新前` |
| 修改配置后，尚未发事件 | `Bus远程验证-刷新前` | `Bus远程验证-刷新前` |
| 只向 A 发一次 `busrefresh` 后 | `Bus远程验证-刷新后` | `Bus远程验证-刷新后` |

刷新接口返回 HTTP `204`。两个实例各有一条绑定到 `spring-learn-bus` 的临时队列；同一次事件通过 RabbitMQ 送达两个实例。

## 六、练习结束

停止自己启动的两个 `user-service` 进程和 Config Server。远程 RabbitMQ 继续运行，不需要关闭服务器或清空队列。

### 可选：使用本地 Docker RabbitMQ

如果需要在本机独立练习，可以在 `config-server` 目录启动仓库中的容器配置：

```bash
docker compose -f compose.rabbitmq.yml up -d --wait
```

本地 AMQP 地址为 `127.0.0.1:5672`，管理页面为 [http://127.0.0.1:15672](http://127.0.0.1:15672)，示例账号和密码均为 `guest`。两个 `user-service` 终端在加载本地环境文件之后、启动 Java 进程之前，分别覆盖这些变量：

```bash
export SPRING_RABBITMQ_HOST=127.0.0.1
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_USERNAME=guest
export SPRING_RABBITMQ_PASSWORD=guest
export SPRING_RABBITMQ_VIRTUAL_HOST=/
```

继续使用上面的两份启动命令，保留不同的端口与实例编号。仅在使用这个本地容器方案时，练习结束后才在 `config-server` 目录关闭本课容器：

```bash
docker compose -f compose.rabbitmq.yml down
```

## 七、本课总结

- Config Server 保存并提供最新配置。
- Bus 使用 RabbitMQ 传播刷新事件；各实例收到事件后重新读取配置。
- 两个实例需要不同的 Bus ID，并且都要连接同一个消息代理、virtual host 和 Bus 目的地。
- 配置文件变化不会自动产生 Bus 事件；本课由 `/actuator/busrefresh` 手动发起。

参考资料：

- [Spring Cloud Bus Quickstart](https://docs.spring.io/spring-cloud-bus/reference/quickstart.html)
- [Spring Cloud Bus Endpoints](https://docs.spring.io/spring-cloud-bus/reference/spring-cloud-bus/bus-endpoints.html)
- [Spring Cloud Bus Addressing Instances](https://docs.spring.io/spring-cloud-bus/reference/spring-cloud-bus/addressing.html)
- [RabbitMQ Docker Official Image](https://hub.docker.com/_/rabbitmq/)
