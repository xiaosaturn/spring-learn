# 第 22 课：Eureka 服务注册与发现

## 1. 目标

解决服务之间地址写死的问题：

```text
订单服务 → http://localhost:9101
```

改成：

```text
订单服务 → user-service
             ↓
          Eureka
             ↓
       找到实际实例
```

整体结构：

```text
                 ┌── user-service：9101
order-service ───┤
                 └── Eureka Server：9761
```

---

## 2. 版本

当前项目使用：

```text
Spring Boot：4.1.1
Spring Cloud：2025.1.3
Java：17
```

Spring Boot 和 Spring Cloud 必须使用兼容版本。

在 `pom.xml` 中添加 Spring Cloud BOM：

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

不要给 Spring Cloud 组件单独指定版本。

---

# 3. 创建 Eureka Server

项目名称：

```text
discovery-server
```

依赖：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

## 启动类

```java
package com.example.discoveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                DiscoveryServerApplication.class,
                args
        );
    }
}
```

## 配置文件

`application.yml`：

```yaml
spring:
  application:
    name: discovery-server

server:
  port: 9761

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false

  server:
    enable-self-preservation: false
```

说明：

```yaml
register-with-eureka: false
```

表示 Eureka Server 不把自己注册到自己。

```yaml
fetch-registry: false
```

表示 Eureka Server 不从其他注册中心获取服务列表。

```yaml
enable-self-preservation: false
```

本地学习时方便快速清理失效实例，生产环境不要随意关闭。

访问控制台：

```text
http://localhost:9761
```

注意：Eureka Server 中不要写：

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

否则它可能会尝试连接自己。

---

# 4. 配置客户端服务

`user-service` 和 `order-service` 都需要添加：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

如果使用带服务名的 `RestClient`，还需要：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

现代 Spring Cloud 中通常不需要额外添加：

```java
@EnableEurekaClient
```

引入 Eureka Client 依赖后会自动注册。

---

# 5. user-service 配置

`application.yml`：

```yaml
spring:
  application:
    name: user-service

server:
  port: 9101

eureka:
  client:
    service-url:
      defaultZone: http://localhost:9761/eureka/

    register-with-eureka: true
    fetch-registry: true

  instance:
    prefer-ip-address: true
```

关键点：

```yaml
spring.application.name: user-service
```

这个名称就是其他服务调用它时使用的服务名。

---

# 6. order-service 配置

`application.yml`：

```yaml
spring:
  application:
    name: order-service

server:
  port: 9102

eureka:
  client:
    service-url:
      defaultZone: http://localhost:9761/eureka/

    register-with-eureka: true
    fetch-registry: true

  instance:
    prefer-ip-address: true
```

注意所有客户端都要使用正确的 Eureka 地址：

```text
http://localhost:9761/eureka/
```

不能继续使用默认的：

```text
http://localhost:8761/eureka/
```

---

# 7. 启动顺序

建议按下面顺序启动：

```text
1. discovery-server：9761
2. user-service：9101
3. order-service：9102
```

打开：

```text
http://localhost:9761
```

应该看到：

```text
USER-SERVICE
ORDER-SERVICE
```

Eureka 页面通常会将服务名显示为大写：

```text
USER-SERVICE
ORDER-SERVICE
```

这是正常现象。

---

# 8. 服务注册流程

```text
user-service 启动
    ↓
读取 spring.application.name
    ↓
连接 http://localhost:9761/eureka/
    ↓
注册到 Eureka
    ↓
Eureka 保存实例地址和端口
```

订单服务也是相同流程。

之后订单服务可以通过：

```text
user-service
```

找到用户服务，而不需要写死：

```text
localhost:9101
```

---

# 9. 使用服务名调用

如果使用 `RestClient`，可以使用服务名：

```java
http://user-service/users/1
```

而不是：

```java
http://localhost:9101/users/1
```

## RestClient 配置

如果同时使用 Eureka 和 `@LoadBalanced`，不要让 Eureka 自己的 HTTP 请求也被负载均衡拦截。

正确配置：

```java
@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient.Builder plainRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean("loadBalancedRestClientBuilder")
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
```

普通 Builder：

```text
给 Eureka 内部请求使用
```

带 `@LoadBalanced` 的 Builder：

```text
给业务服务之间的调用使用
```

客户端示例：

```java
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(
            @Qualifier("loadBalancedRestClientBuilder")
            RestClient.Builder builder
    ) {
        this.restClient = builder
                .baseUrl("http://user-service")
                .build();
    }

    public UserResponse findById(Integer id) {
        return restClient.get()
                .uri("/users/{id}", id)
                .retrieve()
                .body(UserResponse.class);
    }
}
```

后续使用 OpenFeign 后，可以不再手写这部分 `RestClient` 代码。

---

# 10. 多实例测试

可以启动两个用户服务实例：

```text
user-service：9101
user-service：8103
```

两个实例必须使用相同的服务名：

```yaml
spring:
  application:
    name: user-service
```

但端口不同：

```text
9101
8103
```

Eureka 中会显示两个：

```text
USER-SERVICE
USER-SERVICE
```

订单服务调用：

```text
http://user-service/users/1
```

Spring Cloud LoadBalancer 会从多个实例中选择一个。

---

# 11. 常见问题

## 问题一：日志中出现 8761

原因：

```text
Eureka 默认地址是 localhost:8761
```

解决：

检查所有客户端配置：

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:9761/eureka/
```

并搜索项目中是否残留：

```text
8761
defaultZone
default-zone
```

PowerShell：

```powershell
rg -n --hidden -g '!target' "8761|defaultZone|default-zone" .
```

---

## 问题二：`Connection refused`

```text
Connection refused
```

通常表示：

- Eureka Server 没启动
- Eureka 端口写错
- Server 实际运行在 9761，但客户端访问 8761
- 防火墙或端口被占用

检查：

```text
http://localhost:9761
```

是否可以打开。

---

## 问题三：`No servers available for service: localhost`

```text
No servers available for service: localhost
```

通常是因为：

```java
@LoadBalanced
RestClient.Builder
```

被错误地用于 Eureka 内部访问。

解决方式：

- 分离普通 Builder 和负载均衡 Builder
- 或改用 OpenFeign
- 不要给所有 RestClient Builder 都加 `@LoadBalanced`

---

## 问题四：Eureka 页面没有显示服务

检查：

1. 客户端是否添加 Eureka Client 依赖。
2. `spring.application.name` 是否正确。
3. `defaultZone` 是否正确。
4. Eureka Server 是否已经启动。
5. 客户端是否连接到了正确端口。
6. 是否等待了几十秒让注册信息刷新。

---

# 12. 本课重点

```text
Eureka Server：保存服务实例信息
Eureka Client：向注册中心注册自己
spring.application.name：服务名称
defaultZone：注册中心地址
LoadBalancer：从多个实例中选择一个
```

最终调用关系：

```text
order-service
    ↓
调用 http://user-service
    ↓
LoadBalancer
    ↓
Eureka 查询 user-service
    ↓
找到 localhost:9101 或 localhost:8103
    ↓
发送请求
```

下一步是使用 OpenFeign，把服务调用从手写 `RestClient` 改成声明式接口。
