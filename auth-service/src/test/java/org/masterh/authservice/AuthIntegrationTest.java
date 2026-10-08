package org.masterh.authservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.masterh.authservice.model.Account;
import org.masterh.authservice.repository.AccountRepository;
import org.masterh.authservice.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
    }

    @Test
    void registerAndLoginIssuesValidTokenWithoutStoringPlaintextPassword() throws Exception {
        register("zhangsan", "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("注册成功"));

        Account account = accountRepository.findByUsername("zhangsan").orElseThrow();
        assertNotEquals("123456", account.getPassword());
        assertTrue(passwordEncoder.matches("123456", account.getPassword()));

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"zhangsan","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");
        assertTrue(jwtService.isTokenValid(token, "zhangsan"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        register("zhangsan", "123456").andExpect(status().isOk());

        register("zhangsan", "abcdef")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(1, accountRepository.count());
    }

    @Test
    void invalidRegistrationIsRejected() throws Exception {
        register("", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        register("zhangsan", "123456").andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"zhangsan","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String username, String password
    ) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)));
    }
}
