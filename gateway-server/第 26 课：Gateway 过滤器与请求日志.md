# 第 26 课：Gateway 过滤器与请求日志

## 一、本课目标

在第 25 课路由转发的基础上，为 Gateway 增加两类过滤能力：

1. 给下游响应增加统一的网关标识。
2. 记录所有请求的方法、路径、状态码和耗时。

当前服务端口：

```text
discovery-server：9761
user-service：9101
order-service：9102
gateway-server：9120
```

## 二、路由响应过滤器

在 `gateway-server/src/main/resources/application.yml` 的两个路由中配置：

```yaml
filters:
  - StripPrefix=1
  - AddResponseHeader=X-Gateway, gateway-server
```

当前完整路由配置：

```yaml
spring:
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
                - AddResponseHeader=X-Gateway, gateway-server

            - id: order-service-route
              uri: lb://order-service
              predicates:
                - Path=/api/orders/**
              filters:
                - StripPrefix=1
                - AddResponseHeader=X-Gateway, gateway-server
```

`AddResponseHeader` 会给匹配路由的响应添加：

```text
X-Gateway: gateway-server
```

测试用户服务路由：

```powershell
$response = Invoke-WebRequest http://localhost:9120/api/users/1
$response.Headers["X-Gateway"]
```

测试订单服务路由：

```powershell
$response = Invoke-WebRequest http://localhost:9120/api/orders/100
$response.Headers["X-Gateway"]
```

预期结果：

```text
gateway-server
```

## 三、统一请求日志过滤器

文件：

```text
gateway-server/src/main/java/org/masterh/gatewayserver/filter/RequestLogFilter.java
```

```java
package org.masterh.gatewayserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;

            log.info(
                    "Gateway 请求：method={} path={} status={} duration={}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration
            );
        }
    }
}
```

### 代码说明

```java
@Component
```

将过滤器交给 Spring 管理，使其自动加入 Servlet 过滤器链。

```java
extends OncePerRequestFilter
```

保证一次请求在当前过滤器中只处理一次。

```java
filterChain.doFilter(request, response);
```

继续执行后续过滤器和 Gateway 路由。

```java
finally
```

无论请求成功还是失败，都会记录最终状态码和耗时。

## 四、请求流程

```text
客户端请求
    ↓
RequestLogFilter 记录开始时间
    ↓
Gateway 匹配路由
    ↓
StripPrefix 删除 /api
    ↓
LoadBalancer 选择服务实例
    ↓
下游服务返回响应
    ↓
AddResponseHeader 增加 X-Gateway
    ↓
RequestLogFilter 记录状态码和耗时
```

## 五、日志示例

访问：

```text
GET http://localhost:9120/api/users/1
GET http://localhost:9120/api/orders/100
```

Gateway 控制台会输出类似：

```text
Gateway 请求：method=GET path=/api/users/1 status=200 duration=35ms
Gateway 请求：method=GET path=/api/orders/100 status=200 duration=48ms
```

日志字段说明：

| 字段 | 含义 |
|---|---|
| `method` | HTTP 请求方法 |
| `path` | 客户端请求路径 |
| `status` | 最终 HTTP 状态码 |
| `duration` | 请求耗时，单位为毫秒 |

请求日志中不要输出密码、JWT 或完整的 `Authorization` 请求头。

## 六、路由过滤器与全局过滤器的区别

| 类型 | 当前示例 | 生效范围 |
|---|---|---|
| 路由过滤器 | `AddResponseHeader` | 配置该过滤器的路由 |
| Servlet 过滤器 | `RequestLogFilter` | 进入 Gateway 应用的请求 |

## 七、启动与验证

按顺序启动：

```text
1. discovery-server：9761
2. user-service：9101
3. order-service：9102
4. gateway-server：9120
```

验证接口：

```text
GET http://localhost:9120/api/users/1
GET http://localhost:9120/api/orders/100
```

验证结果应同时满足：

- 接口正常返回业务 JSON。
- 响应头包含 `X-Gateway: gateway-server`。
- Gateway 控制台出现请求日志。

## 八、常见问题

### 1. 响应头没有出现

检查：

- 是否修改了 Gateway 的 `application.yml`。
- 是否重启了 `gateway-server`。
- 请求是否匹配 `/api/users/**` 或 `/api/orders/**`。
- 是否把过滤器写在对应 route 的 `filters` 下。

### 2. 控制台没有请求日志

检查：

- `RequestLogFilter` 是否位于 `org.masterh.gatewayserver` 包及其子包内。
- 是否添加 `@Component`。
- 请求是否真的经过 `9120` 端口，而不是直接访问 `9101` 或 `9102`。

### 3. Maven 测试无法复制资源文件

如果出现：

```text
AccessDeniedException: target/classes/application.properties
```

通常是正在运行的 Gateway 进程占用了 `target/classes` 下的文件，或者当前用户没有写权限。先停止 Gateway 运行进程，再重新执行：

```powershell
.\mvnw.cmd test
```

## 九、本课总结

本课完成了：

1. 使用 `AddResponseHeader` 给路由响应增加网关标识。
2. 使用 `OncePerRequestFilter` 统一记录请求日志。
3. 记录 HTTP 方法、请求路径、状态码和耗时。
4. 理解路由过滤器与 Servlet 过滤器的作用范围。

当前 Gateway 处理链：

```text
客户端
    ↓
RequestLogFilter
    ↓
Gateway 路由
    ↓
StripPrefix
    ↓
LoadBalancer
    ↓
下游服务
    ↓
AddResponseHeader
```

## 十、参考资料

- [Spring Cloud Gateway Server Web MVC 自定义过滤器文档](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/writing-custom-predicates-and-filters.html)
- [AddResponseHeader 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/addresponseheader.html)
- [Servlet Filter 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/working-with-servlets-and-filters.html)

