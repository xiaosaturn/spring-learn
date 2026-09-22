# 第 6 课：实现 REST 增删改查

## 学习目标

使用内存集合实现用户 REST API，掌握 `GET`、`POST`、`PUT`、`DELETE`、`@PathVariable`、`@RequestBody` 和 `ResponseEntity`。

## 接口设计

```text
GET    /users       查询全部
GET    /users/{id}  查询单个
POST   /users       新增
PUT    /users/{id}  修改
DELETE /users/{id}  删除
```

Controller 示例：

```java
@RestController
@RequestMapping("/users")
public class UserController {
    private final List<User> users = new ArrayList<>();

    @GetMapping
    public List<User> findAll() {
        return users;
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> findById(@PathVariable int id) {
        return users.stream()
                .filter(user -> user.getId() == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

本课数据保存在内存中，重启应用后会丢失，数据库会在后续课程引入。
