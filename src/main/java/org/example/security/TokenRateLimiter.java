package org.example.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 额度保护：多层限流
 * 1. 按 IP 限流（每分钟 N 次）
 * 2. 全局并发限流（每秒 N 次）
 * 3. 每日 Token 预算
 */
@Component
public class TokenRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TokenRateLimiter.class);

    @Value("${rate-limit.per-ip-per-minute:10}")
    private int perIpPerMinute;

    @Value("${rate-limit.global-per-second:5}")
    private int globalPerSecond;

    @Value("${rate-limit.daily-token-budget:1000000}")
    private long dailyTokenBudget;

    @Value("${rate-limit.per-user-daily:50000}")
    private long perUserDaily;

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    // 按 IP 的请求计数（滑动窗口：每分钟）
    private final ConcurrentHashMap<String, IpWindow> ipWindows = new ConcurrentHashMap<>();

    // 全局每秒计数
    private volatile long globalWindowStart = System.currentTimeMillis();
    private final AtomicLong globalWindowCount = new AtomicLong(0);

    // 每日 Token 消耗
    private volatile LocalDate budgetDate = LocalDate.now();
    private final AtomicLong dailyTokenUsage = new AtomicLong(0);

    // 按用户每日 Token 消耗
    private final ConcurrentHashMap<String, AtomicLong> userDailyUsage = new ConcurrentHashMap<>();

    /**
     * 尝试获取请求许可
     * @param clientIp 客户端 IP
     * @param username 用户名
     * @param estimatedTokens 预估 Token 消耗
     * @return 允许则返回 true，否则返回 false
     */
    public RateLimitResult tryAcquire(String clientIp, String username, int estimatedTokens) {
        if (!enabled) {
            return RateLimitResult.allowed();
        }

        resetIfNewDay();

        // 1. 每日总预算检查
        if (dailyTokenUsage.get() + estimatedTokens > dailyTokenBudget) {
            logger.warn("每日 Token 预算已耗尽: {}/{}", dailyTokenUsage.get(), dailyTokenBudget);
            return RateLimitResult.denied("Token 每日预算已耗尽，请明天再试");
        }

        // 2. 每用户每日限额
        if (username != null) {
            AtomicLong userUsage = userDailyUsage.computeIfAbsent(username, k -> new AtomicLong(0));
            if (userUsage.get() + estimatedTokens > perUserDaily) {
                logger.warn("用户 {} 每日 Token 限额已用尽: {}/{}", username, userUsage.get(), perUserDaily);
                return RateLimitResult.denied("今日 Token 用量已达上限，请明天再试");
            }
        }

        // 3. 全局每秒限流
        long now = System.currentTimeMillis();
        if (now - globalWindowStart > 1000) {
            globalWindowStart = now;
            globalWindowCount.set(0);
        }
        if (globalWindowCount.incrementAndGet() > globalPerSecond) {
            logger.warn("全局限流触发: {}/秒", globalPerSecond);
            return RateLimitResult.denied("系统繁忙，请稍后再试");
        }

        // 4. 按 IP 每分钟限流
        IpWindow ipWindow = ipWindows.computeIfAbsent(clientIp, k -> new IpWindow());
        if (!ipWindow.tryAcquire(perIpPerMinute)) {
            logger.warn("IP 限流触发: ip={}, limit={}/分钟", clientIp, perIpPerMinute);
            return RateLimitResult.denied("请求过于频繁，请稍后再试");
        }

        // 5. 预扣 Token 额度
        dailyTokenUsage.addAndGet(estimatedTokens);
        if (username != null) {
            userDailyUsage.computeIfAbsent(username, k -> new AtomicLong(0)).addAndGet(estimatedTokens);
        }

        return RateLimitResult.allowed();
    }

    /**
     * 记录实际 Token 消耗（调用完成后，用于校准预估值）
     */
    public void recordActualUsage(String username, long actualTokens) {
        // 当前实现使用预扣模式，此方法可用于日志记录
        logger.debug("实际 Token 消耗: user={}, tokens={}", username, actualTokens);
    }

    /**
     * 获取当前使用统计
     */
    public UsageStats getStats() {
        resetIfNewDay();
        return new UsageStats(
                dailyTokenUsage.get(),
                dailyTokenBudget,
                userDailyUsage.size(),
                ipWindows.size()
        );
    }

    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(budgetDate)) {
            budgetDate = today;
            dailyTokenUsage.set(0);
            userDailyUsage.clear();
            logger.info("每日 Token 预算已重置");
        }
    }

    /**
     * IP 滑动窗口（每分钟）
     */
    private static class IpWindow {
        private volatile long windowStart = System.currentTimeMillis();
        private final AtomicLong count = new AtomicLong(0);

        boolean tryAcquire(int maxPerMinute) {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60000) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= maxPerMinute;
        }
    }

    /**
     * 限流结果
     */
    public record RateLimitResult(boolean success, String reason) {
        public static RateLimitResult allowed() { return new RateLimitResult(true, null); }
        public static RateLimitResult denied(String reason) { return new RateLimitResult(false, reason); }
    }

    /**
     * 使用统计
     */
    public record UsageStats(long dailyUsed, long dailyBudget, int activeUsers, int activeIps) {
        public double usagePercent() {
            return dailyBudget > 0 ? (double) dailyUsed / dailyBudget * 100 : 0;
        }
    }
}
