# 第 24 课：Feign 熔断与降级

## 一、本课目标

上一课已经使用 OpenFeign 完成了服务调用，但下游 `user-service` 停止后，订单服务仍然会直接报错。

本课引入 Spring Cloud CircuitBreaker 和 Resilience4j，让 Feign 调用失败时执行降级逻辑：

```text
user-service 正常 → 返回真实用户信息
user-service 超时或停止 → 返回默认用户信息
```

## 二、项目端口

```text
discovery-server：9761
user-service：9101
order-service：9102
```

## 三、添加 Resilience4j 依赖

在 `order-service/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

项目继续使用 Spring Cloud BOM 管理版本：

```xml
<spring-cloud.version>2025.1.3</spring-cloud.version>
```

不要再为 Resilience4j 单独指定版本。

## 四、开启 Feign CircuitBreaker

`order-service/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: order-service

  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
      client:
        config:
          default:
            connectTimeout: 2000
            read-timeout: 3000

server:
  port: 9102

eureka:
  client:
    service-url:
      defaultZone: http://localhost:9761/eureka/

  instance:
    prefer-ip-address: true
```

其中：

```yaml
spring.cloud.openfeign.circuitbreaker.enabled: true
```

表示让 Feign 方法经过 Spring Cloud CircuitBreaker。

## 五、准备降级返回对象

在 `order-service/src/main/java/org/masterh/orderservice/dto/UserResponse.java` 中增加完整构造方法：

```java
public UserResponse(
        Integer id,
        String name,
        Integer age
) {
    this.id = id;
    this.name = name;
    this.age = age;
}
```

这样降级时可以返回可读的默认数据，而不是空对象。

## 六、创建 FallbackFactory

文件：

```text
order-service/src/main/java/org/masterh/orderservice/client/UserClientFallbackFactory.java
```

```java
package org.masterh.orderservice.client;

import org.masterh.orderservice.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class UserClientFallbackFactory
        implements FallbackFactory<UserClient> {

    private static final Logger log =
            LoggerFactory.getLogger(UserClientFallbackFactory.class);

    @Override
    public UserClient create(Throwable cause) {
        log.error("调用 user-service 失败", cause);

        return new UserClient() {
            @Override
            public UserResponse findById(Integer id) {
                return new UserResponse(
                        0,
                        "用户服务暂不可用",
                        0
                );
            }
        };
    }
}
```

`FallbackFactory` 可以拿到原始异常原因，方便记录日志和定位是超时、连接失败还是服务返回错误。

注意导入的是：

```java
import org.springframework.cloud.openfeign.FallbackFactory;
```

不要导入旧版本 Hystrix 的 `FallbackFactory`。

## 七、修改 Feign 客户端

文件：

```text
order-service/src/main/java/org/masterh/orderservice/client/UserClient.java
```

```java
package org.masterh.orderservice.client;

import org.masterh.orderservice.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        fallbackFactory = UserClientFallbackFactory.class
)
public interface UserClient {

    @GetMapping("/users/{id}")
    UserResponse findById(
            @PathVariable("id") Integer id
    );
}
```

当 `user-service` 调用失败时，Feign 会使用 `UserClientFallbackFactory` 创建降级实现。

## 八、调用链

### 正常调用

```text
OrderController
    ↓
UserClient
    ↓
CircuitBreaker
    ↓
Eureka 查找 user-service
    ↓
调用 user-service：9101
    ↓
返回真实用户信息
```

### 服务异常

```text
OrderController
    ↓
UserClient
    ↓
user-service 调用失败
    ↓
UserClientFallbackFactory
    ↓
返回默认用户信息
```

## 九、测试步骤

按顺序启动：

```text
1. discovery-server：9761
2. user-service：9101
3. order-service：9102
```

### 1. 测试正常调用

访问：

```text
GET http://localhost:9102/orders/100
```

预期用户信息为：

```json
{
  "id": 1,
  "name": "张三",
  "age": 20
}
```

### 2. 测试降级

停止 `user-service:9101`，再次访问：

```text
GET http://localhost:9102/orders/100
```

预期订单接口仍能返回，用户信息变为：

```json
{
  "id": 0,
  "name": "用户服务暂不可用",
  "age": 0
}
```

### 3. 测试恢复

重新启动 `user-service:9101`，等待 Eureka 注册完成，再次访问订单接口，应恢复真实用户信息。

## 十、常见问题

### 1. Fallback 没有生效

检查：

- 是否添加 `spring-cloud-starter-circuitbreaker-resilience4j`。
- 是否配置 `spring.cloud.openfeign.circuitbreaker.enabled: true`。
- `UserClientFallbackFactory` 是否添加 `@Component`。
- `@FeignClient` 的 `fallbackFactory` 是否指向正确的类。
- `FallbackFactory` 是否从 `org.springframework.cloud.openfeign` 导入。

### 2. 编译时找不到 `FallbackFactory`

确认没有使用旧的 Hystrix 导入：

```java
import feign.hystrix.FallbackFactory;
```

当前项目应使用：

```java
import org.springframework.cloud.openfeign.FallbackFactory;
```

### 3. 降级返回内容为空

检查 `UserResponse` 是否有带参数构造方法，并确认 `getId()`、`getName()`、`getAge()` getter 仍然存在。

### 4. Eureka 中找不到用户服务

确认用户服务当前端口和注册中心地址：

```yaml
server:
  port: 9101

eureka:
  client:
    service-url:
      defaultZone: http://localhost:9761/eureka/
```

## 十一、本课总结

本课完成了：

1. 添加 Resilience4j CircuitBreaker 依赖。
2. 开启 Feign CircuitBreaker。
3. 创建 `UserClientFallbackFactory`。
4. 为 Feign 客户端配置降级实现。
5. 测试用户服务停止时订单服务仍能返回可控结果。
6. 统一记录当前服务端口：`9101`、`9102`、`9761`。

当前调用关系：

```text
OrderController
    ↓
OpenFeign UserClient
    ↓
CircuitBreaker
    ├── 正常：调用 user-service
    └── 异常：执行 UserClientFallbackFactory
```

## 十二、参考资料

- [Spring Cloud OpenFeign 官方文档](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)
- [Spring Cloud CircuitBreaker Resilience4j 官方文档](https://docs.spring.io/spring-cloud-circuitbreaker/reference/spring-cloud-circuitbreaker-resilience4j.html)

