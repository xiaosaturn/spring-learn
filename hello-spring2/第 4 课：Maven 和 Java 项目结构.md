# 第 4 课：Maven 和 Java 项目结构

## 学习目标

理解 Maven 的作用、标准项目结构和 `pom.xml`，为后续 Spring Boot 项目管理依赖和构建项目。

## Maven 的作用

1. 管理第三方依赖。
2. 编译、测试和打包项目。
3. 统一项目目录结构。

依赖通过 `pom.xml` 声明：

```xml
<dependency>
    <groupId>...</groupId>
    <artifactId>...</artifactId>
    <version>...</version>
</dependency>
```

## 标准目录

```text
pom.xml
src/main/java          正式 Java 代码
src/main/resources     配置文件和资源
src/test/java          测试代码
```

## 环境建议

```text
JDK：17
Maven：3.9 或更高版本
IDE：IntelliJ IDEA
```

Windows 没有全局 `mvn` 命令时，也可以使用 IntelliJ IDEA 自带 Maven 或项目中的 `mvnw.cmd`。Maven 使用的 JDK 应与项目 JDK 保持一致。
