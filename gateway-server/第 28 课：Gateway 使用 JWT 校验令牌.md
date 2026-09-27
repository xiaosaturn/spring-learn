# 第 28 课：Gateway 使用 JWT 校验令牌

## 一、本课目标

第 27 课的 Gateway 只检查请求头是否以 `Bearer ` 开头，`Bearer demo-token` 也可能被放行。本课使用 Spring Security Resource Server，让 Gateway 真正验证 JWT 的签名和有效期。

本课完成后：

```text
没有 Token 的业务请求 → 401
伪造的 Bearer Token → 401
过期 Token → 401
签名正确且未过期的 JWT → 继续路由
/actuator/health → 免登录
/api/public/** → 免登录
```

## 二、添加依赖

在 `gateway-server/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

`spring-boot-starter-oauth2-resource-server` 提供 JWT Bearer Token 的解析和校验能力。

## 三、配置 JWT 密钥

在 `gateway-server/src/main/resources/application.yml` 中配置：

```yaml
jwt:
  secret: ${JWT_SECRET:masterh-learn-spring-jwt-secret-key-1234567890}
```

当前 `hello-spring2` 使用同一个密钥生成 JWT：

```yaml
jwt:
  secret: masterh-learn-spring-jwt-secret-key-1234567890
  expiration-ms: 3600000
```

使用 HMAC 对称加密时，签发方和验证方必须使用相同的密钥。后续独立的 `auth-service` 也要使用这个密钥签发 Token。

## 四、创建 Spring Security 配置

文件：

```text
gateway-server/src/main/java/org/masterh/gatewayserver/config/SecurityConfig.java
```

```java
package org.masterh.gatewayserver.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/api/public/**",
                                "/api/auth/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${jwt.secret}") String secret
    ) {
        SecretKey secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .build();
    }
}
```

这里使用的是 JDK 自带的 `SecretKeySpec`，不需要在 Gateway 中添加 JJWT 依赖。正确的导入是：

```java
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
```

不要写成：

```java
import io.jsonwebtoken.security.Keys;
```

`Keys` 属于 JJWT，当前只在认证服务中用于签发 Token。

## 五、停用旧的格式检查过滤器

第 27 课的 `GatewayAuthFilter` 只检查 `Bearer ` 前缀，不能验证 JWT 签名。本课使用 Spring Security 后，不再让它作为组件注册：

```java
//@Component
```

否则两套鉴权逻辑会同时处理请求。`RequestLogFilter` 可以继续保留。

## 六、请求白名单

白名单配置为：

```java
/actuator/health
/api/public/**
/api/auth/**
```

`/api/auth/**` 是认证接口，用户必须先访问登录接口获取 Token，因此不能要求登录。

## 七、测试 JWT 校验

### 1. 没有 Token

```text
GET http://localhost:9120/api/users/1
```

预期返回：

```text
401 Unauthorized
```

### 2. 使用伪造 Token

```powershell
$headers = @{
    Authorization = "Bearer demo-token"
}

Invoke-WebRequest `
    http://localhost:9120/api/users/1 `
    -Headers $headers
```

预期仍然返回 `401`。只带有 `Bearer ` 前缀的字符串不再算作合法 Token。

### 3. 使用真实 Token

可以从旧的 `hello-spring2` 获取 Token：

```text
POST http://localhost:8101/auth/login
```

独立认证服务完成后，则从：

```text
POST http://localhost:9103/auth/login
```

通过 Gateway 登录时，使用：

```text
POST http://localhost:9120/api/auth/login
```

拿到真实 Token 后访问：

```powershell
$headers = @{
    Authorization = "Bearer 这里替换成真实Token"
}

Invoke-WebRequest `
    http://localhost:9120/api/users/1 `
    -Headers $headers
```

签名正确且未过期时，Gateway 才会继续转发到 `user-service:9101`。

## 八、登录接口必须使用 POST

不能直接在浏览器地址栏访问：

```text
http://localhost:9120/api/auth/login
```

浏览器地址栏发送的是 GET 请求，而登录接口是 POST。PowerShell 测试示例：

```powershell
$body = @{
    username = "test"
    password = "123456"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri http://localhost:9120/api/auth/login `
    -ContentType "application/json" `
    -Body $body
```

## 九、常见问题

### 1. 返回 `请先登录`

检查：

1. Gateway 是否已经完全停止并重新启动。
2. `SecurityConfig` 是否放行 `/api/auth/**`。
3. 登录请求是否使用 POST。
4. 当前运行的是否是最新编译结果。

### 2. 返回签名无效或 401

检查 Gateway 和 Token 签发服务的 `jwt.secret` 是否完全一致。

### 3. `Keys` 找不到

Gateway 只需要 `SecretKeySpec`：

```java
SecretKey secretKey = new SecretKeySpec(
        secret.getBytes(StandardCharsets.UTF_8),
        "HmacSHA256"
);
```

## 十、本课总结

本课完成了：

1. 为 Gateway 添加 Spring Security Resource Server。
2. 使用 `JwtDecoder` 验证 JWT。
3. 校验 JWT 签名和过期时间。
4. 配置健康检查、公共接口和认证接口白名单。
5. 停用只检查 Bearer 前缀的旧过滤器。
6. 理解认证服务签发 Token、Gateway 验证 Token 的职责分工。
7. 完成真实 Token 和伪造 Token 的对比测试。

本课请求流程：

```text
客户端携带 JWT
    ↓
gateway-server:9120
    ↓ JwtDecoder 验证签名和有效期
    ├── 无效 → 401
    └── 有效 → 路由到 user-service 或 order-service
```

下一课创建独立的 `auth-service`，负责注册、登录和 JWT 签发。
