package org.masterh.hellospring2.service;

import org.masterh.hellospring2.dto.RegisterRequest;
import org.masterh.hellospring2.model.Account;
import org.masterh.hellospring2.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @ClassName AccountService
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 17:50
 * @Version 1.0
 **/
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (accountRepository.existsByUsername(
                request.getUsername()
        )) {
            throw new IllegalArgumentException(
                    "用户名已经存在"
            );
        }

        Account account = new Account();

        account.setUsername(request.getUsername());
        account.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        account.setRole("USER");

        accountRepository.save(account);
    }
}

