package org.masterh.hellospring2.service;

import org.masterh.hellospring2.dto.UserCreateRequest;
import org.masterh.hellospring2.dto.UserResponse;
import org.masterh.hellospring2.dto.UserUpdateRequest;
import org.masterh.hellospring2.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.masterh.hellospring2.model.User;
import org.masterh.hellospring2.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;


/**
 * @ClassName UserService
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 9:20
 * @Version 1.0
 **/
@Service
public class UserService {

    private final UserRepository userRepository;

    private static final Logger log =
            LoggerFactory.getLogger(UserService.class);

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Cacheable(cacheNames = "users", key = "#id")
    @Transactional(readOnly = true)
    public UserResponse findById(int id) {
        log.info("开始查询用户，id={}", id);
        User user = findEntityById(id);
        log.info("查询用户成功，id={}", id);
        return toResponse(user);
    }

    @CachePut(cacheNames = "users", key = "#result.id")
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        User user = new User();

        user.setName(request.getName());
        user.setAge(request.getAge());

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }

    @CachePut(cacheNames = "users", key = "#id")
    @Transactional
    public UserResponse update(int id, UserUpdateRequest request) {
        User user = findEntityById(id);

        user.setName(request.getName());
        user.setAge(request.getAge());


        /**
         * 只要满足下面条件，JPA 会自动执行更新：
         * 1. 方法处于事务中。
         * 2. 实体是从 Repository 查询出来的。
         * 3. 修改了实体属性。
         * 4. 方法正常执行完成。
         * 事务提交时，JPA 会发现实体发生了变化，自动执行：
         * 这叫脏检查。
         * 写上 save() 通常也可以，但对于事务中已经存在的实体，一般不需要再次调用。
         */
        // User savedUser = userRepository.save(user);

        return toResponse(user);
    }

    @CacheEvict(cacheNames = "users", key = "#id")
    @Transactional
    public void deleteById(int id) {
        User user = findEntityById(id);
        userRepository.delete(user);
    }

    public List<User> findByName(String name) {
        return userRepository.findByNameContaining(name);
    }

    public List<User> findByMinAge(Integer minAge) {
        return userRepository.findByAgeGreaterThanEqual(minAge);
    }

    public List<User> findAllSorted(String direction) {
        Sort.Direction sortDirection;

        if ("desc".equalsIgnoreCase(direction)) {
            sortDirection = Sort.Direction.DESC;
        } else {
            sortDirection = Sort.Direction.ASC;
        }

        Sort sort = Sort.by(sortDirection, "age");

        return userRepository.findAll(sort);
    }

    public Page<User> findPage(int page, int size) {
        if (page < 0) {
            page = 0;
        }

        if (size < 1) {
            size = 10;
        }

        if (size > 100) {
            size = 100;
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "id")
        );

        return userRepository.findAll(pageable);
    }

    private User findEntityById(int id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getAge()
        );
    }
}
