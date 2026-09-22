# 第 12 课：Spring 事务与 @Transactional

## 学习目标

理解事务的原子性，并使用 `@Transactional` 保证一组数据库操作要么全部成功，要么全部回滚。

## 查询事务

```java
@Transactional(readOnly = true)
public UserResponse findById(int id) {
    User user = findEntityById(id);
    return toResponse(user);
}
```

`readOnly = true` 表示方法只查询数据。

## 写事务

```java
@Transactional
public UserResponse create(UserCreateRequest request) {
    User user = new User();
    user.setName(request.getName());
    user.setAge(request.getAge());
    return toResponse(userRepository.save(user));
}
```

如果事务方法中抛出未处理的运行时异常，Spring 会回滚本次事务。事务通常放在 Service 层，因为 Service 才能完整表达一个业务操作。

## 注意事项

- 优先导入 `org.springframework.transaction.annotation.Transactional`。
- 不要把事务边界放在 Controller。
- 转账等多步操作必须在同一事务中完成。
