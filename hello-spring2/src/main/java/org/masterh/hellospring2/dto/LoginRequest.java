package org.masterh.hellospring2.dto;

/**
 * @ClassName LoginRequest
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 17:33
 * @Version 1.0
 **/
public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
