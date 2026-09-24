# 第 27 课：Gateway 统一鉴权与白名单

## 一、本课目标

在 Gateway 层统一检查请求是否携带登录凭证，并为健康检查等公共接口配置白名单。

```text
discovery-server：9761
user-service：9101
order-service：9102
gateway-server：9120
```

本课完成后：

```text
业务接口没有 Authorization → 返回 401
业务接口携带 Bearer → 继续转发
/actuator/health → 不需要登录
/api/public/** → 不需要登录
```

## 二、添加 Actuator

在 `gateway-server/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

在 `application.yml` 中配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - info
  endpoint:
    health:
      show-details: never
```

访问：

```text
GET http://localhost:9120/actuator/health
```

预期返回：

```json
{
  "status": "UP"
}
```

白名单只负责放行请求，Actuator 依赖才会真正创建 `/actuator/health` 接口。

## 三、统一鉴权过滤器

文件：

```text
gateway-server/src/main/java/org/masterh/gatewayserver/filter/GatewayAuthFilter.java
```

```java
package org.masterh.gatewayserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isWhiteList(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(
                    MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
            );
            response.getWriter().write(
                    "{\"message\":\"请先登录\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhiteList(String path) {
        return path.startsWith("/actuator/health")
                || path.startsWith("/api/public");
    }
}
```

## 四、关键知识点

### `@Component`

必须添加 `@Component`，否则 Spring 不会创建这个过滤器，鉴权代码不会执行。

### 白名单

```java
return path.startsWith("/actuator/health")
        || path.startsWith("/api/public");
```

健康检查和公共接口直接放行，不要求登录。

### Bearer 检查

当前阶段只检查请求头格式：

```text
Authorization: Bearer demo-token
```

还没有验证 JWT 签名、过期时间和用户信息。

### UTF-8 响应

```java
response.setCharacterEncoding(StandardCharsets.UTF_8.name());
response.setContentType("application/json;charset=UTF-8");
```

这样中文错误信息不会显示成 `????`。

## 五、测试未登录请求

不携带请求头访问：

```text
GET http://localhost:9120/api/users/1
```

预期返回：

```text
401 Unauthorized
```

```json
{
  "message": "请先登录"
}
```

## 六、测试携带 Bearer 请求

```powershell
$headers = @{
    Authorization = "Bearer demo-token"
}

Invoke-WebRequest `
  http://localhost:9120/api/users/1 `
  -Headers $headers
```

Gateway 会继续执行路由，将请求转发到 `user-service:9101`。

订单接口同样需要携带请求头：

```powershell
Invoke-WebRequest `
  http://localhost:9120/api/orders/100 `
  -Headers $headers
```

## 七、测试健康检查白名单

不携带 `Authorization` 也可以访问：

```text
GET http://localhost:9120/actuator/health
```

预期返回：

```json
{
  "status": "UP"
}
```

## 八、请求处理流程

```text
请求 /api/orders/100
    ↓
GatewayAuthFilter
    ↓
检查 Authorization
    ├── 没有 Bearer → 返回 401
    └── 携带 Bearer → Gateway 路由
                         ↓
                    order-service
```

健康检查流程：

```text
请求 /actuator/health
    ↓
匹配白名单
    ↓
跳过鉴权
    ↓
Actuator 返回 UP
```

## 九、本课总结

本课完成了：

1. 在 Gateway 中添加 Actuator。
2. 暴露 `/actuator/health` 和 `/actuator/info`。
3. 创建统一鉴权过滤器。
4. 为健康检查和公共接口设置白名单。
5. 未登录业务请求返回 `401`。
6. 使用 UTF-8 返回中文错误信息。
7. 理解当前 Bearer 检查和真正 JWT 校验的区别。

当前 Gateway 处理链：

```text
客户端
    ↓
GatewayAuthFilter
    ↓
RequestLogFilter
    ↓
Gateway 路由
    ↓
LoadBalancer
    ↓
下游服务
```

下一课可以把当前的 Bearer 格式检查升级为真正的 JWT 签名和过期时间校验。

