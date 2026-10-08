# 第 30 课：Spring Cloud Config 集中配置

## 一、本课目标

第 29 课已经有多个独立服务。现在每个服务仍在自己的 `application.yml` 中保存端口、Eureka 地址等配置。本课增加 `config-server`，先让 `user-service` 从它读取配置，理解配置服务端、配置仓库和配置客户端的关系。

本课只迁移 `user-service` 的非敏感配置。`auth-service` 和 `gateway-server` 的 JWT 配置暂时保持原样。

```text
config-server/config-repo/user-service.yml
                ↓
config-server:9130
                ↓ 启动时读取
user-service:9101
```

## 二、为什么需要配置中心

服务变多后，逐个修改配置文件容易遗漏。Config Server 对外提供统一的配置读取接口，客户端在启动时按应用名和环境读取对应文件。

本课使用本地文件系统作为配置仓库，便于在一台电脑上练习。它适合入门和测试；多实例生产部署需要可靠且共享的配置来源，后续可以改用 Git 等后端。

## 三、配置服务端

`config-server/pom.xml` 引入 `spring-cloud-config-server`，版本由项目使用的 Spring Cloud `2025.1.3` BOM 管理。启动类添加 `@EnableConfigServer`。

`config-server/src/main/resources/application.yml` 的关键配置：

```yaml
spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: file:./config-repo

server:
  address: 127.0.0.1
  port: 9130
```

`native` 表示从文件系统读取配置。`file:./config-repo` 相对于配置服务的工作目录，因此应从 `config-server` 目录启动它。
本地练习把配置服务绑定在 `127.0.0.1`，只供这台电脑上的客户端访问。

配置仓库中的 `config-server/config-repo/user-service.yml` 包含：

```yaml
server:
  port: 9101

eureka:
  client:
    service-url:
      defaultZone: http://localhost:9761/eureka/
  instance:
    prefer-ip-address: true

lesson:
  user-name: 张三（来自配置中心）
```

文件名 `user-service.yml` 与客户端的 `spring.application.name: user-service` 对应。

## 四、配置客户端

`user-service/pom.xml` 引入 `spring-cloud-starter-config`。客户端自己的 `application.yml` 只保留启动时必须知道的信息：

```yaml
spring:
  application:
    name: user-service
  config:
    import: configserver:http://localhost:9130
```

`spring.config.import` 告诉 Spring Boot 到哪里读取远程配置。这里没有 `optional:`，所以配置服务不可用时，`user-service` 启动会失败。这可以避免服务在缺少预期配置时继续运行。

`UserController` 用 `@Value("${lesson.user-name}")` 读取配置，并放入 `/users/{id}` 的响应。这样可以直接观察远程配置是否生效。

不需要创建 `bootstrap.yml`；当前的 Config Data Import 机制使用 `spring.config.import`。

## 五、启动与验证

从各模块目录启动，顺序如下：

1. 在 `discovery-server` 目录运行 `bash ./mvnw spring-boot:run`。
2. 在 `config-server` 目录运行 `bash ./mvnw spring-boot:run`。
3. 在 `user-service` 目录运行 `bash ./mvnw spring-boot:run`。

先访问配置服务：

```bash
curl http://localhost:9130/user-service/default
```

返回的 `propertySources` 应包含 `file:config-repo/user-service.yml`、`server.port: 9101` 和 `lesson.user-name`。

再访问用户服务：

```bash
curl http://localhost:9101/users/1
```

预期响应：

```json
{"id":1,"name":"张三（来自配置中心）","age":20}
```

这个结果说明客户端启动时取得了远程配置，并把它注入控制器。

## 六、练习

把 `config-server/config-repo/user-service.yml` 中的 `lesson.user-name` 改成你喜欢的名字。先重新请求配置服务，确认它读取到新值；然后重启 `user-service` 并请求 `/users/1`，观察响应变化。

本课没有配置运行时刷新。已经启动的 `user-service` 不会因为文件变化而自动更新控制器中的值。

## 七、常见问题

### 配置服务启动了，但客户端连不上

检查客户端的 `configserver:http://localhost:9130`、配置服务端口，以及启动顺序。

### `/user-service/default` 没有返回配置文件

确认从 `config-server` 目录启动，并检查 `config-repo/user-service.yml` 的文件名。`file:./config-repo` 依赖当前工作目录。

### 单元测试提示没有 `spring.config.import`

加入 Config Client 后，测试环境也要明确说明是否读取配置。仓库中的 `user-service/src/test/resources/application.yml` 使用 `optional:configserver:` 并关闭配置客户端，让普通上下文测试不依赖运行中的配置服务。

## 八、本课总结

- Config Server 从配置仓库读取文件，并通过 HTTP 提供配置。
- Config Client 根据应用名和环境在启动时获取配置。
- `spring.config.import` 决定客户端如何连接配置服务；去掉 `optional:` 表示必须连接成功。
- 本地文件系统后端适合学习；运行时刷新、Git 后端和配置权限留到后续课程。

参考资料：

- [Spring Cloud Config Server](https://docs.spring.io/spring-cloud-config/reference/server.html)
- [Spring Cloud Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html)
- [File System Backend](https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/file-system-backend.html)
