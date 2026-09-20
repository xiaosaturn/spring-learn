package org.masterh.hellospring2.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.masterh.hellospring2.model.User;

import java.util.List;

/**
 * @ClassName UserRepository
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 11:30
 * @Version 1.0
 **/
public interface UserRepository extends JpaRepository<User, Integer> {
    // 姓名模糊查询
    List<User> findByNameContaining(String name);

    // 查询年龄大于等于指定值的用户
    List<User> findByAgeGreaterThanEqual(Integer age);

    // 判断姓名是否存在
    boolean existsByName(String name);
}
