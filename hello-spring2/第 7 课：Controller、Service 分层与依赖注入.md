# 第 7 课：Controller、Service 分层与依赖注入

## 学习目标

将接口接收、业务处理和数据操作分开，理解构造器注入和 Spring Bean。

```text
Controller：接收 HTTP 请求
Service：处理业务规则
Repository：访问数据
```

## Service

```java
@Service
public class UserService {
    private final List<User> users = new ArrayList<>();

    public List<User> findAll() {
        return users;
    }
}
```

## Controller 注入 Service

```java
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
}
```

Spring 会创建并管理 `UserService`，再通过构造方法注入 Controller。构造器注入比在字段上直接注入更容易测试，也能保证依赖不可变。
