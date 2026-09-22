# 第 16 课：从 HTTP Basic 改成 JWT 登录

## 学习目标

使用 JWT 完成登录认证，让客户端在后续请求中携带令牌访问受保护接口。

## JWT 依赖

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

配置密钥和有效期：

```yaml
jwt:
  secret: learn-spring-jwt-secret-key-1234567890
  expiration-ms: 3600000
```

登录成功后生成 JWT；请求通过过滤器解析 `Authorization: Bearer <token>`，验证成功后将认证信息放入 SecurityContext。

密钥至少 32 个字符，不能提交真实密钥到公共仓库。
