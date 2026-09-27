# 第 29 课：创建独立认证服务 auth-service

## 一、本课目标

将登录、注册、账号认证和 JWT 签发从 `hello-spring2` 中独立出来，创建一个认证微服务。

本课完成后的服务结构：

```text
discovery-server：9761
auth-service：9103
user-service：9101
order-service：9102
gateway-server：9120
```

职责划分：

```text
auth-service：注册、登录、密码校验、JWT 签发
user-service：用户业务信息
order-service：订单业务
gateway-server：统一入口、路由、JWT 校验
discovery-server：服务注册与发现
hello-spring2：保留为原来的 Spring Boot 综合练习项目
```

## 二、创建项目

项目目录：

```text
H:\SpringCloudLearning\auth-service
```

项目使用 Spring Boot 4.1.1、Spring Cloud 2025.1.3、Java 17，端口为 9103。

主要依赖包括 WebMVC、Validation、JPA、Security、Eureka Client、Actuator、MySQL 和 JJWT。

## 三、认证服务的核心功能

### 1. 注册

```http
POST /auth/register
```

请求示例：

```json
{
  "username": "test",
  "password": "123456"
}
```

注册流程：

```text
接收用户名和密码
    ↓
检查用户名是否已存在
    ↓
BCrypt 加密密码
    ↓
保存 Account
```

### 2. 登录

```http
POST /auth/login
```

登录时由 `AuthenticationManager` 校验用户名和密码，认证成功后由 `JwtService` 生成 JWT。

返回示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJ..."
  }
}
```

密码保存前使用 BCrypt 加密，不能保存明文密码。

## 四、JWT 配置

文件：

```text
auth-service/src/main/resources/application.yml
```

```yaml
jwt:
  secret: ${JWT_SECRET:masterh-learn-spring-jwt-secret-key-1234567890}
  expiration-ms: 3600000
```

当前使用 HMAC 对称密钥：

```text
auth-service：使用密钥签发 JWT
gateway-server：使用同一个密钥验证 JWT
```

如果两个服务的密钥不同，Gateway 会认为 JWT 签名无效并返回 401。以后可以升级为 RSA，由认证服务使用私钥签发、Gateway 使用公钥验证。

## 五、数据库配置

当前为了复用已有账号数据，暂时使用 `hello_db` 数据库中的 `accounts` 表：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hello_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: root
```

`allowPublicKeyRetrieval=true` 用于解决 MySQL 连接时的 `Public Key Retrieval is not allowed` 错误。以后认证服务完全独立时，可以切换为单独的 `auth_db`。

## 六、Gateway 添加认证路由

在 `gateway-server/src/main/resources/application.yml` 中添加：

```yaml
- id: auth-service-route
  uri: lb://auth-service
  predicates:
    - Path=/api/auth/**
  filters:
    - StripPrefix=1
```

外部请求：

```text
/api/auth/login
```

经过 Gateway 的 `StripPrefix=1` 后转发为：

```text
/auth/login
```

因此：

```text
通过 Gateway：      POST http://localhost:9120/api/auth/login
直接访问认证服务：  POST http://localhost:9103/auth/login
```

## 七、Gateway 白名单

Gateway 的 Spring Security 配置需要放行认证接口：

```java
.requestMatchers(
        "/actuator/health",
        "/api/public/**",
        "/api/auth/**"
).permitAll()
```

当前 `GatewayAuthFilter` 已经不再作为 Spring Bean 使用：

```java
//@Component
```

真正的 JWT 校验由 Spring Security Resource Server 完成，避免两套鉴权逻辑重复执行。

## 八、登录测试

登录接口必须使用 POST，不能直接在浏览器地址栏访问，因为地址栏发送的是 GET 请求。

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

登录成功后，把返回的 Token 放入请求头：

```powershell
$headers = @{
    Authorization = "Bearer 这里替换成真实Token"
}

Invoke-WebRequest `
    http://localhost:9120/api/users/1 `
    -Headers $headers
```

## 九、启动顺序

```text
1. discovery-server
2. auth-service
3. user-service
4. order-service
5. gateway-server
```

健康检查：

```text
GET http://localhost:9103/actuator/health
GET http://localhost:9120/actuator/health
```

## 十、问题排查

### 1. `Public Key Retrieval is not allowed`

检查 MySQL JDBC URL 是否包含：

```text
allowPublicKeyRetrieval=true
```

### 2. 登录接口返回“请先登录”

检查以下内容：

1. 请求是否使用 `POST`。
2. 地址是否为 `http://localhost:9120/api/auth/login`。
3. Gateway SecurityConfig 是否放行 `/api/auth/**`。
4. Gateway 是否已经完全重启，避免仍使用旧的编译类。
5. `auth-service` 是否已经注册到 Eureka。

### 3. 密钥不一致

确保 Gateway 和 auth-service 使用相同的 `jwt.secret`。

## 十一、本课总结

本课完成了：

1. 创建独立的 `auth-service`。
2. 将注册和登录功能从 `hello-spring2` 中独立出来。
3. 使用 BCrypt 保存密码。
4. 使用 JWT 签发登录令牌。
5. 将 `auth-service` 注册到 Eureka。
6. 在 Gateway 中添加 `/api/auth/**` 路由。
7. 配置认证接口白名单。
8. 通过 Gateway 完成登录请求。
9. 修复 MySQL 公钥获取连接问题。

当前认证流程：

```text
客户端
    ↓ POST /api/auth/login
gateway-server:9120
    ↓ StripPrefix=1
auth-service:9103/auth/login
    ↓
校验账号密码并生成 JWT
    ↓
客户端携带 JWT 请求业务接口
```
