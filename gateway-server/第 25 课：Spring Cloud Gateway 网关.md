# 第 25 课：Spring Cloud Gateway 网关

## 一、本课目标

为多个微服务增加统一访问入口，让客户端不再直接访问各个服务端口。

```text
客户端
    ↓
Gateway：9120
    ├── user-service：9101
    └── order-service：9102
```

Gateway 负责路由转发，Eureka 负责服务发现，LoadBalancer 负责从服务实例中选择目标实例。

## 二、项目版本与端口

```text
Java：17
Spring Boot：4.1.1
Spring Cloud：2025.1.3

discovery-server：9761
user-service：9101
order-service：9102
gateway-server：9120
```

## 三、Gateway 依赖

`gateway-server/pom.xml` 中使用当前版本的 Gateway Server Web MVC：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
</dependency>
```

同时添加：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

项目通过 Spring Cloud BOM 管理版本：

```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2025.1.3</spring-cloud.version>
</properties>
```

## 四、启动类

```java
package org.masterh.gatewayserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                GatewayServerApplication.class,
                args
        );
    }
}
```

Gateway 项目不需要额外添加 `@EnableEurekaClient`，引入 Eureka Client 依赖后会自动注册。

## 五、Gateway 配置

文件：

```text
gateway-server/src/main/resources/application.yml
```

```yaml
spring:
  application:
    name: gateway-server

  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: user-service-route
              uri: lb://user-service
              predicates:
                - Path=/api/users/**
              filters:
                - StripPrefix=1

            - id: order-service-route
              uri: lb://order-service
              predicates:
                - Path=/api/orders/**
              filters:
                - StripPrefix=1

server:
  port: 9120

eureka:
  client:
    service-url:
      defaultZone: http://localhost:9761/eureka/

  instance:
    prefer-ip-address: true
```

## 六、路由配置说明

### 用户服务路由

```yaml
id: user-service-route
uri: lb://user-service
predicates:
  - Path=/api/users/**
filters:
  - StripPrefix=1
```

客户端访问：

```text
GET http://localhost:9120/api/users/1
```

Gateway 的处理流程：

```text
/api/users/1
    ↓ 匹配 /api/users/**
删除 /api
    ↓
/users/1
    ↓
Eureka 查找 user-service
    ↓
转发到 localhost:9101/users/1
```

### 订单服务路由

客户端访问：

```text
GET http://localhost:9120/api/orders/100
```

Gateway 会将请求转发为：

```text
/orders/100
```

再通过 Eureka 找到：

```text
order-service：9102
```

之后 `order-service` 内部继续通过 OpenFeign 调用 `user-service`。

## 七、启动顺序

```text
1. discovery-server：9761
2. user-service：9101
3. order-service：9102
4. gateway-server：9120
```

Eureka 控制台：

```text
http://localhost:9761
```

预期可以看到：

```text
USER-SERVICE
ORDER-SERVICE
GATEWAY-SERVER
```

## 八、接口测试

### 1. 通过 Gateway 访问用户服务

```text
GET http://localhost:9120/api/users/1
```

预期返回：

```json
{
  "id": 1,
  "name": "张三",
  "age": 20
}
```

### 2. 通过 Gateway 访问订单服务

```text
GET http://localhost:9120/api/orders/100
```

预期返回：

```json
{
  "id": 100,
  "productName": "Spring Cloud 课程",
  "amount": 199,
  "user": {
    "id": 1,
    "name": "张三",
    "age": 20
  }
}
```

## 九、直接访问与网关访问

以前客户端直接访问：

```text
http://localhost:9101/users/1
http://localhost:9102/orders/100
```

现在统一通过 Gateway：

```text
http://localhost:9120/api/users/1
http://localhost:9120/api/orders/100
```

以后客户端只需要知道 Gateway 的地址，后端服务可以更换端口或增加实例，而不需要修改客户端代码。

## 十、常见问题

### 1. Gateway 启动失败

检查 Gateway 依赖是否为：

```xml
spring-cloud-starter-gateway-server-webmvc
```

不要混用旧版 Gateway MVC 依赖。

### 2. `No instances available for user-service`

检查：

- `user-service` 是否启动。
- Eureka 控制台是否存在 `USER-SERVICE`。
- `defaultZone` 是否为 `http://localhost:9761/eureka/`。
- 是否添加 `spring-cloud-starter-loadbalancer`。
- 服务名是否写成 `lb://user-service`。

### 3. 返回 404

检查 `StripPrefix` 和下游接口路径是否对应：

```text
/api/users/1 → /users/1
/api/orders/100 → /orders/100
```

### 4. 仍然直接访问旧端口

直接访问 `9101` 或 `9102` 仍然可以作为调试方式，但客户端统一入口应使用 `9120`。

## 十一、本课总结

本课完成了：

1. 创建 `gateway-server` 项目。
2. 使用 Gateway Server Web MVC 依赖。
3. 注册 Gateway 到 Eureka。
4. 使用 `lb://user-service` 配置用户服务路由。
5. 使用 `lb://order-service` 配置订单服务路由。
6. 使用 `StripPrefix` 将外部路径转换成内部接口路径。
7. 将统一访问入口设置为 `http://localhost:9120`。

当前请求链路：

```text
客户端
    ↓
Gateway：9120
    ↓
Eureka + LoadBalancer
    ├── user-service：9101
    └── order-service：9102
```

## 十二、验证结果

`gateway-server` 已执行 Maven 测试，Spring Boot 上下文加载成功，Gateway 成功向 Eureka 注册：

```text
GATEWAY-SERVER/gateway-server:9120 - registration status: 204
```

## 十三、参考资料

- [Spring Cloud Gateway Server Web MVC 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc.html)
- [Gateway Server Web MVC Starter 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/starter.html)

