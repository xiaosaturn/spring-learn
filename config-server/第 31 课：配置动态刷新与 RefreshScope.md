# 第 31 课：配置动态刷新与 RefreshScope

## 一、本课目标

第 30 课让 `user-service` 在启动时从 Config Server 读取 `lesson.user-name`。但配置文件修改后，已经运行的客户端仍然使用旧值。本课让客户端在收到刷新请求后重新读取配置，无需为每次配置修改而重启服务。

```text
修改 config-repo/user-service.yml
              ↓
Config Server 读到新值
              ↓
POST user-service 的 /actuator/refresh
              ↓
下一次请求 /users/1 返回新名字
```

## 二、客户端增加刷新能力

`user-service/pom.xml` 增加 `spring-boot-starter-actuator`。Actuator 提供管理端点，Spring Cloud 在此基础上提供 `/actuator/refresh`。

`UserController` 增加 `@RefreshScope`。它的构造函数使用 `@Value("${lesson.user-name}")` 取得名字。收到刷新请求后，Spring Cloud 清除这个作用域中旧的对象；下一次访问时创建新对象，构造函数便会读取更新后的值。

```java
@RestController
@RefreshScope
@RequestMapping("/users")
public class UserController {
    private final String userName;

    public UserController(@Value("${lesson.user-name}") String userName) {
        this.userName = userName;
    }
}
```

`@RefreshScope` 只作用于标注的对象。不要认为调用一次刷新端点就会自动重建应用中的所有对象或清空所有业务状态。

## 三、管理端点

`user-service/src/main/resources/application.yml` 在本地配置管理入口：

```yaml
management:
  server:
    address: 127.0.0.1
    port: 9104
  endpoints:
    web:
      exposure:
        include: health,refresh
```

业务接口仍在配置中心指定的 `9101` 端口；刷新端点使用单独的 `9104` 端口，并绑定本机地址。刷新端点会改变运行时配置，不应直接开放给外部调用者。这里把管理入口写在客户端本地配置中，便于清楚地看到它的访问位置。

## 四、动手验证

从各模块目录启动 `discovery-server`、`config-server`、`user-service`：

```bash
bash ./mvnw spring-boot:run
```

这次代码增加了依赖和注解，因此需要先启动新版本的 `user-service`。后面的配置修改不用重启它。

先请求业务接口，记录当前名字：

```bash
curl http://localhost:9101/users/1
```

修改 `config-server/config-repo/user-service.yml` 中的 `lesson.user-name`。再次请求配置服务和业务接口：

```bash
curl http://localhost:9130/user-service/default
curl http://localhost:9101/users/1
```

此时配置服务应返回新名字，而业务接口仍返回旧名字。然后从本机通知客户端刷新：

```bash
curl -X POST http://127.0.0.1:9104/actuator/refresh
curl http://localhost:9101/users/1
```

刷新响应中应包含 `lesson.user-name`；之后业务接口返回新名字。如果配置没有变化，刷新响应可能是空数组。

## 五、理解刷新边界

- 修改配置文件本身不会主动通知客户端。本课需要手动调用刷新端点。
- 刷新请求发给某一个 `user-service` 实例；有多个实例时，每个实例都需要刷新。后续可学习 Spring Cloud Bus 如何向多个实例广播变更。
- `@RefreshScope` 对象在下一次使用时重新创建。因此它适合这里的配置读取示例；已经运行中的请求不会在执行到一半时切换名字。
- 如果删除一个已有配置项，不能简单假设刷新会把它恢复为默认值；先明确配置缺失时的处理方式。

## 六、常见问题

### 刷新端点返回 404

确认已用新代码启动 `user-service`，并检查 Actuator 依赖、`management.endpoints.web.exposure.include` 和管理端口 `9104`。

### 刷新成功，但名字不变

先请求 `http://localhost:9130/user-service/default`，确认 Config Server 已读到新值。再确认刷新请求发给当前运行的 `user-service`，并检查 `UserController` 上的 `@RefreshScope`。

### `9104` 端口无法访问

管理服务只绑定 `127.0.0.1`，请在运行 `user-service` 的同一台电脑上调用。检查是否已有程序占用端口。

## 七、本课总结

Config Server 提供最新配置，`/actuator/refresh` 让客户端重新读取配置，`@RefreshScope` 让指定对象在后续访问时使用新值。这三个环节共同完成了本课的运行时刷新。

参考资料：

- [Spring Cloud Refresh Scope](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/application-context-services.html#refresh-scope)
- [Spring Boot Actuator Endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Boot Actuator HTTP Monitoring](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
