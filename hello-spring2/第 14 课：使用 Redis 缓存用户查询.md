# 第 14 课：使用 Redis 缓存用户查询

## 学习目标

使用 Redis 缓存用户查询结果，减少重复访问 MySQL。

```text
第一次查询：Redis 没有 → 查询 MySQL → 写入 Redis
第二次查询：直接从 Redis 返回
```

## 启动 Redis

```powershell
docker run -d --name learn-redis -p 6379:6379 redis:7-alpine
docker exec -it learn-redis redis-cli ping
```

返回 `PONG` 表示 Redis 正常。

## 依赖和配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2s
```

Service 查询方法可以使用 `@Cacheable`，新增和修改使用 `@CachePut`，删除使用 `@CacheEvict`。

## 注意事项

更换序列化器后要清理旧缓存；通用 JSON 序列化器需要正确保存类型信息，否则读取结果可能变成 `LinkedHashMap`。
