# 第 15 课：Spring Security 基础鉴权

## 学习目标

理解认证与授权的区别，使用 Spring Security 的 HTTP Basic 保护接口。

```text
认证 Authentication：确认你是谁
授权 Authorization：确认你能做什么
```

常见状态码：

| 状态码 | 含义 |
|---|---|
| `401` | 未登录或认证失败 |
| `403` | 已登录但没有权限 |

## 基础配置

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()
                    .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .build();
}
```

添加 `spring-boot-starter-security` 后，未配置的接口默认会受到保护。HTTP Basic 适合理解认证流程，实际项目会在下一课改为 JWT。
