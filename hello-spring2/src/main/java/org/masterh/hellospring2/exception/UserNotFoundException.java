package org.masterh.hellospring2.exception;

/**
 * @ClassName UserNotFoundException
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 11:06
 * @Version 1.0
 **/
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(int id) {
        super("用户不存在，id：" + id);
    }
}
