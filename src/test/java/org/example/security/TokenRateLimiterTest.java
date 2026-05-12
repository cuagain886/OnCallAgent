package org.example.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 额度保护限流测试
 */
class TokenRateLimiterTest {

    private TokenRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenRateLimiter();
        setField(rateLimiter, "perIpPerMinute", 5);
        setField(rateLimiter, "globalPerSecond", 10);
        setField(rateLimiter, "dailyTokenBudget", 100000L);
        setField(rateLimiter, "perUserDaily", 10000L);
        setField(rateLimiter, "enabled", true);
    }

    // ==================== 正常请求测试 ====================

    @Test
    void tryAcquire_shouldAllowNormalRequest() {
        TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("192.168.1.1", "user1", 2000);
        assertTrue(result.success(), "Normal request should be allowed");
    }

    @Test
    void tryAcquire_shouldAllowMultipleRequestsUnderLimit() {
        for (int i = 0; i < 5; i++) {
            TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("192.168.1.1", "user1", 1000);
            assertTrue(result.success(), "Request " + (i + 1) + " should be allowed");
        }
    }

    // ==================== IP 限流测试 ====================

    @Test
    void tryAcquire_shouldDenyWhenIpLimitExceeded() {
        // perIpPerMinute = 5
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("10.0.0.1", "user1", 1000);
        }
        // 第 6 次应该被拒绝
        TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("10.0.0.1", "user1", 1000);
        assertFalse(result.success(), "Should be denied when IP limit exceeded");
        assertNotNull(result.reason());
    }

    @Test
    void tryAcquire_shouldAllowDifferentIpsIndependently() {
        // 用完 IP1 的配额
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("10.0.0.1", "user1", 1000);
        }
        // IP2 应该仍然可用
        TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("10.0.0.2", "user2", 1000);
        assertTrue(result.success(), "Different IP should have independent limit");
    }

    // ==================== 每日预算测试 ====================

    @Test
    void tryAcquire_shouldDenyWhenDailyBudgetExceeded() {
        // dailyTokenBudget = 100000
        // 先消耗大部分预算
        rateLimiter.tryAcquire("192.168.1.1", "user1", 90000);
        rateLimiter.tryAcquire("192.168.1.1", "user1", 9000);

        // 此时已用 99000，剩余 1000
        // 请求 2000 tokens 应该被拒绝
        TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("192.168.1.1", "user1", 2000);
        assertFalse(result.success(), "Should be denied when daily budget exceeded");
    }

    // ==================== 用户限额测试 ====================

    @Test
    void tryAcquire_shouldDenyWhenUserDailyLimitExceeded() {
        // perUserDaily = 10000
        rateLimiter.tryAcquire("192.168.1.1", "user1", 9000);
        // 再请求 2000，总计 11000 > 10000
        TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("192.168.1.1", "user1", 2000);
        assertFalse(result.success(), "Should be denied when user daily limit exceeded");
    }

    @Test
    void tryAcquire_shouldTrackDifferentUsersIndependently() {
        // user1 用完配额
        rateLimiter.tryAcquire("192.168.1.1", "user1", 10000);
        // user2 应该还有配额
        TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("192.168.1.1", "user2", 5000);
        assertTrue(result.success(), "Different users should have independent limits");
    }

    // ==================== 禁用限流测试 ====================

    @Test
    void tryAcquire_shouldAllowAllWhenDisabled() {
        setField(rateLimiter, "enabled", false);

        // 即使超过所有限制也应该允许
        for (int i = 0; i < 100; i++) {
            TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire("10.0.0.1", "user1", 100000);
            assertTrue(result.success(), "Should allow all when disabled");
        }
    }

    // ==================== 统计测试 ====================

    @Test
    void getStats_shouldReturnCorrectUsage() {
        rateLimiter.tryAcquire("192.168.1.1", "user1", 5000);
        rateLimiter.tryAcquire("192.168.1.2", "user2", 3000);

        TokenRateLimiter.UsageStats stats = rateLimiter.getStats();
        assertEquals(8000, stats.dailyUsed());
        assertEquals(100000, stats.dailyBudget());
        assertTrue(stats.activeUsers() >= 2);
    }

    @Test
    void getStats_shouldCalculateUsagePercent() {
        // 使用不超过 perUserDaily(10000) 的值
        rateLimiter.tryAcquire("192.168.1.1", "user1", 5000);

        TokenRateLimiter.UsageStats stats = rateLimiter.getStats();
        // 5000 / 100000 = 5%
        assertEquals(5.0, stats.usagePercent(), 0.1);
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
