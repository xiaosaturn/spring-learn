package org.masterh.hellospring2;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.masterh.hellospring2.repository.AccountRepository;
import org.masterh.hellospring2.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @ClassName AuthenticationIntegrationTest
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/19 20:17
 * @Version 1.0
 * @AutoConfigureMockMvc 创建 MockMvc，用于模拟 HTTP 请求，但不会真正占用 8101 端口
 **/
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthenticationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterLoginAndAccessProtectedApi()
            throws Exception {

        // 第一步：注册
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "zhangsan",
                                          "password": "123456"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 第二步：登录并获得 JWT
        MvcResult loginResult = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "zhangsan",
                                          "password": "123456"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String responseBody = loginResult
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(
                responseBody,
                "$.data.token"
        );

        // 第三步：使用 JWT 访问受保护接口
        mockMvc.perform(
                        get("/users")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void normalUserShouldNotCreateUser() throws Exception {
        register("lisi", "123456");

        String token = login("lisi", "123456");

        mockMvc.perform(
                        post("/users")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "新用户",
                                      "age": 20
                                    }
                                    """)
                )
                .andExpect(status().isForbidden());
    }

    private void register(
            String username,
            String password
    ) throws Exception {
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "%s",
                                      "password": "%s"
                                    }
                                    """.formatted(username, password))
                )
                .andExpect(status().isOk());
    }

    private String login(
            String username,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "%s",
                                      "password": "%s"
                                    }
                                    """.formatted(username, password))
                )
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.token"
        );
    }

    @Test
    void loginShouldFailWhenPasswordIsWrong()
            throws Exception {
        register("wangwu", "123456");

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "wangwu",
                                      "password": "错误密码"
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized());
    }
}
