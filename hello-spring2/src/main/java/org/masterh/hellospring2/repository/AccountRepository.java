package org.masterh.hellospring2.repository;

import org.masterh.hellospring2.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @InterfaceName AccountRepository
 * @Description TODO
 * @Author XiaoSaturn
 * @Date 2026/9/19 17:47
 * @Version 1.0
 **/
public interface AccountRepository extends JpaRepository<Account, Integer> {
    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);
}
