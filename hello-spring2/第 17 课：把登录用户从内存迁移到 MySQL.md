# 第 17 课：把登录用户从内存迁移到 MySQL

## 学习目标

将写死在内存中的登录用户迁移到数据库，并把登录账号与业务用户实体分离。

## 账号实体

```java
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    private String role = "USER";
    private boolean enabled = true;
}
```

密码必须保存经过 `PasswordEncoder` 加密后的值，不能保存明文。通过 `AccountRepository` 按用户名查询账号，再交给 Spring Security 完成认证。

## 设计要点

- `Account` 表保存登录信息。
- `User` 表保存业务资料。
- 用户名设置唯一约束。
- 注册时校验用户名不能重复。
- 登录成功后继续签发 JWT。
