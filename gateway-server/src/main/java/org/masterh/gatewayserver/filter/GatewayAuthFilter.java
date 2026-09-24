package org.masterh.gatewayserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


/**
 * @ClassName GatewayAuthFilter
 * @Description TODO
 * @Author MasterH
 * @Date 2026/9/24 9:03
 * @Version 1.0
 **/
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isWhiteList(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(
                    MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
            );
            response.getWriter().write(
                    "{\"message\":\"请先登录\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhiteList(String path) {
        return path.startsWith("/actuator/health")
                || path.startsWith("/api/public");
    }
}
