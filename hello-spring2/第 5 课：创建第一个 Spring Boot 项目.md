# 第 5 课：创建第一个 Spring Boot 项目

## 学习目标

使用 Spring Initializr 创建第一个 Spring Boot 项目，理解启动类和 Spring Web 依赖。

## 创建参数

```text
Language：Java
Build system：Maven
JDK：17
Packaging：Jar
依赖：Spring Web
项目名：hello-spring
```

## 启动类

```java
@SpringBootApplication
public class HelloSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelloSpringApplication.class, args);
    }
}
```

`@SpringBootApplication` 组合了配置、自动配置和组件扫描能力。`SpringApplication.run` 会创建 Spring 容器并启动内置 Web 服务器。

## 本课成果

能够启动 Spring Boot 应用，理解 `src/main/java`、`src/main/resources` 和 `pom.xml` 的作用，为后续创建 Controller 做准备。
