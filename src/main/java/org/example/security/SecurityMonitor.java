package org.example.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全监控：异常检测、告警、自动封禁
 */
@Component
@EnableScheduling
public class SecurityMonitor {

    private static final Logger logger = LoggerFactory.getLogger(SecurityMonitor.class);

    @Autowired
    private TokenRateLimiter rateLimiter;

    @Value("${rate-limit.alert-threshold:0.8}")
    private double alertThreshold;

    @Value("${rate-limit.auto-ban-threshold:50}")
    private int autoBanThreshold;

    // 被封禁的 IP
    private final ConcurrentHashMap<String, LocalDateTime> bannedIps = new ConcurrentHashMap<>();

    // IP 调用计数（用于异常检测）
    private final ConcurrentHashMap<String, Integer> ipCallCounts = new ConcurrentHashMap<>();

    /**
     * 检查 IP 是否被封禁
     */
    public boolean isBanned(String ip) {
        LocalDateTime banTime = bannedIps.get(ip);
        if (banTime == null) return false;
        // 自动解封：1 小时后
        if (LocalDateTime.now().isAfter(banTime.plusHours(1))) {
            bannedIps.remove(ip);
            return false;
        }
        return true;
    }

    /**
     * 记录 IP 调用（用于异常检测）
     */
    public void recordCall(String ip) {
        ipCallCounts.merge(ip, 1, Integer::sum);
    }

    /**
     * 手动封禁 IP
     */
    public void banIp(String ip) {
        bannedIps.put(ip, LocalDateTime.now());
        logger.warn("[SECURITY] IP 已被封禁: {}", ip);
    }

    /**
     * 手动解封 IP
     */
    public void unbanIp(String ip) {
        bannedIps.remove(ip);
        logger.info("[SECURITY] IP 已解封: {}", ip);
    }

    /**
     * 获取封禁列表
     */
    public Map<String, LocalDateTime> getBannedIps() {
        return Map.copyOf(bannedIps);
    }

    /**
     * 每分钟检查：Token 消耗告警
     */
    @Scheduled(fixedRate = 60000)
    public void checkTokenBudget() {
        TokenRateLimiter.UsageStats stats = rateLimiter.getStats();
        double usageRate = stats.usagePercent() / 100.0;

        if (usageRate > alertThreshold) {
            logger.warn("[SECURITY] Token 消耗告警: 已用 {}/{} ({}%)",
                    stats.dailyUsed(), stats.dailyBudget(), String.format("%.0f", usageRate * 100));
        }

        if (usageRate > 0.95) {
            logger.error("[SECURITY] Token 预算即将耗尽！已用 {}%", String.format("%.0f", usageRate * 100));
        }
    }

    /**
     * 每 5 分钟检查：异常 IP 检测
     */
    @Scheduled(fixedRate = 300000)
    public void checkAnomalyIps() {
        for (Map.Entry<String, Integer> entry : ipCallCounts.entrySet()) {
            String ip = entry.getKey();
            int count = entry.getValue();
            if (count > autoBanThreshold && !isBanned(ip)) {
                logger.warn("[SECURITY] 异常 IP 自动封禁: ip={}, calls={}", ip, count);
                banIp(ip);
            }
        }
        // 重置计数
        ipCallCounts.clear();
    }
}
