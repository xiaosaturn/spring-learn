# 第 9 课：把内存数据改成 MySQL 数据库

## 学习目标

使用 Spring Data JPA 将用户数据从内存集合迁移到 MySQL，使数据在应用重启后仍然保留。

## 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

## 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hello_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: 你的 MySQL 密码
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

先创建数据库：

```sql
CREATE DATABASE hello_db DEFAULT CHARACTER SET utf8mb4;
```

## 实体与仓库

使用 `@Entity` 将 `User` 映射为表，使用 `JpaRepository<User, Integer>` 完成基本增删改查。
