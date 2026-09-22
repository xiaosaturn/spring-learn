# 第 10 课：JPA 条件查询、排序和分页

## 学习目标

使用 Spring Data JPA 方法名查询实现模糊查询、范围查询、排序和分页。

## 方法名查询

```java
public interface UserRepository extends JpaRepository<User, Integer> {
    List<User> findByNameContaining(String name);
    List<User> findByAgeGreaterThanEqual(Integer age);
    boolean existsByName(String name);
}
```

常见关键词：

| 关键词 | 含义 |
|---|---|
| `Containing` | 包含、模糊匹配 |
| `GreaterThanEqual` | 大于等于 |
| `Between` | 区间查询 |
| `OrderBy` | 排序 |
| `ExistsBy` | 判断是否存在 |

## 分页

```java
Page<User> findByNameContaining(String name, Pageable pageable);
```

调用时：

```java
Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
Page<User> result = userRepository.findAll(pageable);
```

分页接口应明确页码、页大小和排序规则，避免一次性返回大量数据。
