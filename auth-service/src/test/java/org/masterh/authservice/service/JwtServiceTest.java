package org.masterh.authservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String SECRET = "auth-service-test-secret-key-at-least-32-bytes-long";
    private final JwtService jwtService = new JwtService(SECRET, 3_600_000);

    @Test
    void generatedTokenContainsSubjectAndIsValidForThatUser() {
        String token = jwtService.generateToken("zhangsan");

        assertEquals("zhangsan", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, "zhangsan"));
        assertFalse(jwtService.isTokenValid(token, "lisi"));
    }

    @Test
    void rejectsExpiredTokenAndTokenSignedWithAnotherKey() {
        Date now = new Date();
        String expiredToken = Jwts.builder()
                .subject("zhangsan")
                .issuedAt(new Date(now.getTime() - 120_000))
                .expiration(new Date(now.getTime() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        String wrongSignature = Jwts.builder()
                .subject("zhangsan")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "another-test-secret-key-at-least-32-bytes-long"
                                .getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(jwtService.isTokenValid(expiredToken, "zhangsan"));
        assertFalse(jwtService.isTokenValid(wrongSignature, "zhangsan"));
    }
}
