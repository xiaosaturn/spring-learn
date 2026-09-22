# 第 11 课：DTO 与统一接口返回格式

## 学习目标

使用 DTO 隔离接口数据和数据库实体，避免客户端直接修改不应修改的字段，并统一成功与失败的返回结构。

## 请求 DTO

```java
public class UserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    private String name;

    @NotNull(message = "年龄不能为空")
    private Integer age;
}
```

## 响应 DTO

```java
public record UserResponse(Integer id, String name, Integer age) {
}
```

Service 负责实体与 DTO 转换：

```java
private UserResponse toResponse(User user) {
    return new UserResponse(user.getId(), user.getName(), user.getAge());
}
```

## 统一返回

```java
public record ApiResponse<T>(boolean success, T data, String message) {
}
```

这样可以让客户端统一读取 `success`、`data` 和 `message`，也避免实体字段直接暴露到 API。
