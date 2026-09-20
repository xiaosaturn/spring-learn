package org.masterh.hellospring2.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.masterh.hellospring2.dto.UserResponse;
import org.masterh.hellospring2.model.User;
import org.masterh.hellospring2.repository.UserRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.masterh.hellospring2.exception.UserNotFoundException;
import org.masterh.hellospring2.dto.UserCreateRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

/**
 * @ClassName UserServiceTest
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 19:38
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    /**
     * @Mock注解表示创建假的 UserRepository。
     * 测试不会连接真正的 MySQL
     */
    @Mock
    private UserRepository userRepository;

    /**
     * @InjectMocks 表示创建 UserService，并把假的 Repository 注入进去。
     */
    @InjectMocks
    private UserService userService;

    @Test
    void findByIdShouldReturnUser() {
        // 准备测试数据
        User user = new User(
                1,
                "张三",
                20
        );

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        // 执行被测试方法
        UserResponse result = userService.findById(1);

        // 验证结果
        assertEquals(1, result.getId());
        assertEquals("张三", result.getName());
        assertEquals(20, result.getAge());

        // 验证 Repository 被调用
        verify(userRepository).findById(1);
    }

    @Test
    void findByIdShouldThrowWhenUserNotFound() {
        when(userRepository.findById(99))
                .thenReturn(Optional.empty());

        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> userService.findById(99)
                );

        assertEquals(
                "用户不存在，id：99",
                exception.getMessage()
        );

        verify(userRepository).findById(99);
    }

    @Test
    void createShouldSaveAndReturnUser() {
        UserCreateRequest request =
                new UserCreateRequest();

        request.setName("李四");
        request.setAge(21);

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(10);
                    return user;
                });

        UserResponse result = userService.create(request);

        assertEquals(10, result.getId());
        assertEquals("李四", result.getName());
        assertEquals(21, result.getAge());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void deleteShouldDeleteExistingUser() {
        User user = new User(
                1,
                "张三",
                20
        );

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        userService.deleteById(1);

        verify(userRepository).findById(1);
        verify(userRepository).delete(user);
    }

    @Test
    void deleteShouldThrowWhenUserNotFound() {
        when(userRepository.findById(99))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.deleteById(99)
        );

        verify(userRepository).findById(99);
    }
}
