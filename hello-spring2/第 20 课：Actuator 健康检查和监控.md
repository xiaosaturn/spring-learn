# 第 20 课：Actuator 健康检查和监控

## 学习目标

使用 Spring Boot Actuator 暴露健康检查、应用信息、指标和 Prometheus 数据，为服务注册、监控和容器编排提供基础。

## 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

info:
  app:
    name: ${spring.application.name}
    version: 1.0.0
```

常用地址：

```text
/actuator/health
/actuator/info
/actuator/metrics
/actuator/prometheus
```

生产环境不要无条件暴露全部端点，也不要把密码、密钥等敏感信息写入 `info`。
