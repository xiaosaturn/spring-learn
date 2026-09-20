package org.masterh.hellospring2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


/**
 * @ClassName HelloController
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 9:03
 * @Version 1.0
 **/
@RestController
@RequestMapping("/hello")
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello Spring Boot";
    }

    @GetMapping("/greet")
    public String greet(
            @RequestParam(defaultValue = "同学") String name
    ) {
        return "你好，" + name;
    }

    @GetMapping("/users")
    public List<User> users() {
        List<User> users = new ArrayList<>();

        users.add(new User(1, "张三", 20));
        users.add(new User(2, "李四", 21));

        return users;
    }

    private final List<User> users = new ArrayList<>();

    private int nextId = 3;

    public HelloController() {
        users.add(new User(1, "张三", 20));
        users.add(new User(2, "李四", 21));
    }

    // 查询全部用户
    @GetMapping
    public List<User> findAll() {
        return users;
    }

    // 根据 id 查询用户
    @GetMapping("/{id}")
    public ResponseEntity<User> findById(@PathVariable int id) {
        for (User user : users) {
            if (user.getId() == id) {
                return ResponseEntity.ok(user);
            }
        }

        return ResponseEntity.notFound().build();
    }

    // 新增用户
    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user) {
        user.setId(nextId++);
        users.add(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(user);
    }

    // 修改用户
    @PutMapping("/{id}")
    public ResponseEntity<User> update(
            @PathVariable int id,
            @RequestBody User request
    ) {
        for (User user : users) {
            if (user.getId() == id) {
                user.setName(request.getName());
                user.setAge(request.getAge());

                return ResponseEntity.ok(user);
            }
        }

        return ResponseEntity.notFound().build();
    }

    // 删除用户
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        for (User user : users) {
            if (user.getId() == id) {
                users.remove(user);
                return ResponseEntity.noContent().build();
            }
        }

        return ResponseEntity.notFound().build();
    }

    public static class User {
        private int id;
        private String name;
        private int age;

        public User() {
        }

        public User(int id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}
