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

在 `config-server` 目录运行：

```bash
docker compose -f compose.rabbitmq.yml up -d --wait
```

`compose.rabbitmq.yml` 把 RabbitMQ 的 AMQP 端口 `5672` 和管理页面端口 `15672` 映射到本机地址。Spring Boot 在本机练习时使用默认的 RabbitMQ 连接地址。可访问 `http://127.0.0.1:15672` 查看 RabbitMQ 管理页面；本地示例使用默认账号 `guest`、密码 `guest`。

启动 Config Server：

```bash
bash ./mvnw spring-boot:run
```

## 四、启动两个客户端实例

先在 `user-service` 目录打包：

```bash
bash ./mvnw package
```

然后在两个终端分别运行：

```bash
java -jar target/user-service-0.0.1-SNAPSHOT.jar \
  --server.port=9102 \
  --management.server.port=9105 \
  --spring.application.index=1 \
  --eureka.client.enabled=false
```

```bash
java -jar target/user-service-0.0.1-SNAPSHOT.jar \
  --server.port=9103 \
  --management.server.port=9106 \
  --spring.application.index=2 \
  --eureka.client.enabled=false
```

两个业务端口和管理端口各不相同。`spring.application.index` 也各不相同，让两个实例拥有不同的 Bus ID；否则事件可能被误判为来自同一个实例。这次实验直接访问用户服务，因此关闭 Eureka 客户端，减少无关的启动依赖。

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

两个名字都应变成新值。如果只有一个实例改变，先检查两个实例是否连接到同一个 RabbitMQ，再检查它们的 Bus ID 是否不同。

如果启动日志反复出现 `Connection refused`，先确认 RabbitMQ 容器已经处于健康状态，再发送刷新请求。

与第 31 课比较：若改用 `POST http://127.0.0.1:9105/actuator/refresh`，只会刷新实例 A；实例 B 不会因这次请求而更新。

## 六、练习结束

停止两个 `user-service` 进程和 Config Server，然后在 `config-server` 目录关闭本课的 RabbitMQ 容器：

```bash
docker compose -f compose.rabbitmq.yml down
```

## 七、本课总结

- Config Server 保存并提供最新配置。
- Bus 使用 RabbitMQ 传播刷新事件；各实例收到事件后重新读取配置。
- 两个实例需要不同的 Bus ID，并且都要连接同一个消息代理。
- 配置文件变化不会自动产生 Bus 事件；本课由 `/actuator/busrefresh` 手动发起。

参考资料：

- [Spring Cloud Bus Quickstart](https://docs.spring.io/spring-cloud-bus/reference/quickstart.html)
- [Spring Cloud Bus Endpoints](https://docs.spring.io/spring-cloud-bus/reference/spring-cloud-bus/bus-endpoints.html)
- [Spring Cloud Bus Addressing Instances](https://docs.spring.io/spring-cloud-bus/reference/spring-cloud-bus/addressing.html)
- [RabbitMQ Docker Official Image](https://hub.docker.com/_/rabbitmq/)
