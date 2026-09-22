# 第 1 课：Java 面向对象和集合

## 学习目标

掌握类与对象、封装、构造方法，以及使用 `List` 和 `ArrayList` 保存数据，完成一个简单的学生管理程序。

## 核心内容

```java
class Student {
    private int id;
    private String name;
    private int age;

    public Student(int id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }
}
```

- 类是对象的模板，对象是类的实例。
- 使用 `private` 隐藏字段，通过 getter/setter 访问数据。
- 构造方法用于创建对象时初始化数据。
- `List<Student>` 适合保存有顺序的学生集合。

```java
List<Student> students = new ArrayList<>();
students.add(new Student(1, "张三", 20));
students.add(new Student(2, "李四", 21));
```

## 本课成果

实现学生的添加、遍历、查询和删除，理解面向对象代码与直接堆叠在 `main` 方法中的代码的区别。
