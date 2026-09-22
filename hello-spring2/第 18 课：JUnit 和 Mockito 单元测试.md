# 第 18 课：JUnit 和 Mockito 单元测试

## 学习目标

使用 JUnit 5 和 Mockito 测试 Service 业务逻辑，不启动完整 Spring 容器，也不连接真实数据库。

## 测试依赖

通常由 `spring-boot-starter-test` 提供：

- JUnit 5
- Mockito
- Spring Test
- AssertJ

## Service 单元测试

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findByIdReturnsUser() {
        User user = new User(1, "张三", 20);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        UserResponse result = userService.findById(1);

        assertThat(result.name()).isEqualTo("张三");
        verify(userRepository).findById(1);
    }
}
```

还要覆盖查不到用户、重复用户名、参数非法等分支。单元测试的重点是业务规则，而不是框架本身。
