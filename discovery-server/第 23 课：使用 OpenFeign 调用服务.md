# 第 23 课：使用 OpenFeign 调用服务

## 一、本课目标

上一课中，`order-service` 使用 `RestClient` 调用 `user-service`，需要手动编写请求地址、请求方法和响应转换代码。

本课使用 OpenFeign 将远程 HTTP 调用改成声明式接口调用：

```text
手写 RestClient 请求 → OpenFeign 声明式接口
```

完成本课后，订单服务可以通过 Feign 客户端调用用户服务，并继续使用 Eureka 进行服务发现、使用 Spring Cloud LoadBalancer 选择服务实例。

## 二、项目版本

```text
Java：17
Spring Boot：4.1.1
Spring Cloud：2025.1.3
```

Spring Boot 与 Spring Cloud 的版本需要保持兼容，Spring Cloud 组件版本由 BOM 统一管理。

## 三、添加 OpenFeign 依赖

在 `order-service/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

如果项目没有自动引入负载均衡组件，再补充：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

Spring Cloud BOM 示例：

```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2025.1.3</spring-cloud.version>
</properties>
```

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

不要再给 OpenFeign 或 LoadBalancer 单独指定版本。

## 四、开启 Feign 客户端

在 `order-service` 的启动类上添加 `@EnableFeignClients`：

```java
package com.example.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

`@EnableFeignClients` 会扫描 Feign 客户端接口，并为接口创建代理对象。

## 五、创建 Feign 客户端接口

文件：`order-service/src/main/java/com/example/orderservice/client/UserClient.java`

```java
package com.example.orderservice.client;

import com.example.orderservice.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/users/{id}")
    UserResponse findById(@PathVariable("id") Integer id);
}
```

### 关键点

```java
@FeignClient(name = "user-service")
```

这里的 `user-service` 必须与用户服务的应用名称一致：

```yaml
spring:
  application:
    name: user-service
```

`@GetMapping` 描述用户服务的接口路径，`@PathVariable("id")` 将方法参数填充到 URL 路径中。

## 六、在订单服务中使用 Feign

订单控制器通过构造方法注入 `UserClient`：

```java
package com.example.orderservice.controller;

import com.example.orderservice.client.UserClient;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final UserClient userClient;

    public OrderController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Integer id) {
        UserResponse user = userClient.findById(1);

        return new OrderResponse(
                id,
                "Spring Cloud 课程",
                199,
                user
        );
    }
}
```

业务代码只需要调用：

```java
UserResponse user = userClient.findById(1);
```

Feign 会自动完成以下工作：

```text
调用 UserClient 接口
    ↓
通过 Eureka 查找 user-service
    ↓
通过 LoadBalancer 选择实例
    ↓
发送 GET /users/1
    ↓
将 JSON 转换成 UserResponse
```

## 七、删除旧的 RestClient 调用代码

切换到 Feign 后，`order-service` 中原来只用于调用用户服务的代码可以删除：

- 手写的 `UserClient` 实现类
- `RestClientConfig.java`
- `RestClient.Builder` 配置
- `RestClient` 调用代码

Eureka Client 和 LoadBalancer 依赖继续保留，因为 Feign 仍然需要通过服务名发现并选择服务实例。

## 八、配置 Feign 超时

在 `order-service/src/main/resources/application.yml` 中设置超时时间：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 3000
```

配置含义：

| 配置项 | 含义 |
|---|---|
| `connect-timeout` | 建立连接最多等待 2 秒 |
| `read-timeout` | 等待服务返回数据最多等待 3 秒 |

微服务调用必须设置超时，避免下游服务异常时一直占用请求线程。

## 九、启动与测试

按以下顺序启动服务：

```text
1. discovery-server：9761
2. user-service：9101
3. order-service：9102
```

打开 Eureka 控制台：

```text
http://localhost:9761
```

确认已经注册：

```text
USER-SERVICE
ORDER-SERVICE
```

然后访问订单接口：

```text
GET http://localhost:9102/orders/100
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

这说明订单服务已经通过 OpenFeign 找到并调用了 `user-service`。

## 十、RestClient 与 OpenFeign 对比

| 对比项 | RestClient | OpenFeign |
|---|---|---|
| 调用方式 | 手写 HTTP 请求 | 声明式接口 |
| 请求代码 | 较多 | 较少 |
| 服务名调用 | 需要配置负载均衡 Builder | 使用 `@FeignClient` 声明服务名 |
| JSON 转换 | 手动指定响应类型 | 根据接口返回类型自动转换 |
| 可读性 | 一般 | 更接近业务接口 |
| 微服务使用 | 可以使用 | 常用方案 |

## 十一、常见问题

### 1. 启动时报找不到 Feign 客户端

检查以下内容：

- 是否添加 `spring-cloud-starter-openfeign` 依赖。
- 启动类是否添加 `@EnableFeignClients`。
- `UserClient` 是否位于启动类包或扫描范围内。
- `@FeignClient` 是否导入了 `org.springframework.cloud.openfeign.FeignClient`。

### 2. `No servers available for service: user-service`

通常说明 Eureka 没有找到可用的用户服务实例。检查：

- `user-service` 是否已经启动。
- Eureka 控制台是否存在 `USER-SERVICE`。
- `@FeignClient(name = "user-service")` 中的名称是否正确。
- 两个服务的 `defaultZone` 是否指向同一个 Eureka Server。
- 是否保留了 `spring-cloud-starter-loadbalancer` 依赖。

### 3. 服务名大小写不一致

服务名应统一使用：

```text
user-service
```

它对应：

```yaml
spring:
  application:
    name: user-service
```

### 4. 下游服务停止后请求失败

这是当前版本的正常现象：Feign 已经完成调用，但还没有配置熔断和降级处理。下一课将使用 CircuitBreaker/Resilience4j 处理下游服务故障。

## 十二、本课总结

本课完成了以下内容：

1. 添加 OpenFeign 依赖。
2. 使用 `@EnableFeignClients` 开启 Feign 扫描。
3. 使用 `@FeignClient(name = "user-service")` 声明远程服务。
4. 使用注解定义远程接口和路径参数。
5. 在订单服务中注入并调用 Feign 客户端。
6. 配置 Feign 连接超时和读取超时。
7. 通过 Eureka 和 LoadBalancer 完成服务发现与实例选择。

当前调用链：

```text
OrderController
    ↓
UserClient（OpenFeign 接口）
    ↓
Eureka 查找 user-service
    ↓
LoadBalancer 选择实例
    ↓
UserController
```

## 十三、下一课

第 24 课：Feign 熔断与降级。

目标：

```text
用户服务正常 → 返回真实用户信息
用户服务异常 → 返回默认数据或友好错误
```
