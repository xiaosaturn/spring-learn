# 第 2 课：方法拆分、异常处理和 Map 集合

## 学习目标

将程序拆分成职责清晰的方法，学习 `try-catch`、主动抛出异常，以及使用 `Map` 根据学号快速查找学生。

## 方法拆分

每个方法只负责一件事：

```java
addStudent();
findStudent();
updateStudent();
deleteStudent();
```

这体现了单一职责，便于阅读、测试和修改。

## 异常处理

```java
try {
    int result = 10 / 0;
} catch (ArithmeticException e) {
    System.out.println("除数不能为 0");
}
```

遇到业务不合法的情况，可以主动抛出异常：

```java
throw new IllegalArgumentException("学号已经存在");
```

## Map 集合

```java
Map<Integer, Student> students = new HashMap<>();
students.put(1, new Student(1, "张三", 20));
Student student = students.get(1);
```

`Map` 使用 key 定位 value，比遍历 `List` 更适合按学号查询。

## 本课成果

完成按学号新增、查询、修改、删除，并能对重复学号和查无此人等情况给出明确错误。
