package org.example.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类测试
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        setField(jwtUtil, "secret", "TestSecretKeyForJwtUtil2026!MustBeLongEnough");
        setField(jwtUtil, "expiration", 3600000L);  // 1 小时
    }

    @Test
    void generateToken_shouldReturnNonNull() {
        String token = jwtUtil.generateToken("testuser", "user");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        String token = jwtUtil.generateToken("testuser", "user");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_shouldReturnFalseForInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
    }

    @Test
    void validateToken_shouldReturnFalseForEmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    void getUsernameFromToken_shouldReturnCorrectUsername() {
        String token = jwtUtil.generateToken("alice", "admin");
        assertEquals("alice", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    void getRoleFromToken_shouldReturnCorrectRole() {
        String token = jwtUtil.generateToken("bob", "user");
        assertEquals("user", jwtUtil.getRoleFromToken(token));
    }

    @Test
    void generateToken_shouldGenerateDifferentTokensForDifferentUsers() {
        String token1 = jwtUtil.generateToken("user1", "user");
        String token2 = jwtUtil.generateToken("user2", "user");
        assertNotEquals(token1, token2);
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        // 创建一个已过期的 Token
        JwtUtil expiredJwtUtil = new JwtUtil();
        setField(expiredJwtUtil, "secret", "TestSecretKeyForJwtUtil2026!MustBeLongEnough");
        setField(expiredJwtUtil, "expiration", -1000L);  // 已过期

        String token = expiredJwtUtil.generateToken("testuser", "user");
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void getUsernameFromToken_shouldWorkWithChineseUsername() {
        String token = jwtUtil.generateToken("张三", "user");
        assertEquals("张三", jwtUtil.getUsernameFromToken(token));
    }

    // ==================== 辅助方法 ====================

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
