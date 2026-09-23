package org.masterh.orderservice.dto;

/**
 * @ClassName UserResponse
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/20 9:55
 * @Version 1.0
 **/
public class UserResponse {
    private Integer id;
    private String name;
    private Integer age;

    public UserResponse() {
    }

    public UserResponse(
            Integer id,
            String name,
            Integer age
    ) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getAge() {
        return age;
    }
}
