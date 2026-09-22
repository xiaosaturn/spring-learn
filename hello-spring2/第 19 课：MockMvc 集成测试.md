# 第 19 课：MockMvc 集成测试

## 学习目标

使用 H2 临时数据库和 MockMvc 测试完整 HTTP 流程：

```text
注册 → 登录 → 获得 JWT → 携带 JWT 访问接口
```

## 测试环境

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop
```

测试环境不修改本机 MySQL 数据。

## MockMvc 示例

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerLoginAndAccess() throws Exception {
        // 注册、登录并保存 JWT
        // 使用 Authorization: Bearer <token> 访问受保护接口
    }
}
```

集成测试同时验证 Controller、Service、Repository、Security 和数据库配置是否能够协同工作。
