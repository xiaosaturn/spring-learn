package org.masterh.orderservice.client;

import org.apache.juli.logging.Log;
import org.masterh.orderservice.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
/**
 * @ClassName UserClientFallbackFactory
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/23 9:02
 * @Version 1.0
 **/
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    private static final Logger log = LoggerFactory.getLogger(UserClientFallbackFactory.class);

    @Override
    public UserClient create(Throwable cause) {
        log.error("调用 user-service 失败", cause);

        return new UserClient() {
            @Override
            public UserResponse findById(Integer id) {
                return new UserResponse(
                        0,
                        "用户服务暂不可用",
                        0
                );
            }
        };
    }
}
