package org.example.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 安全管理 API（仅管理员可访问）
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    @Autowired
    private TokenRateLimiter rateLimiter;

    @Autowired
    private SecurityMonitor securityMonitor;

    /**
     * 获取 Token 使用统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestAttribute("currentRole") String role) {
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }

        TokenRateLimiter.UsageStats stats = rateLimiter.getStats();
        return ResponseEntity.ok(Map.of(
                "dailyUsed", stats.dailyUsed(),
                "dailyBudget", stats.dailyBudget(),
                "usagePercent", String.format("%.1f%%", stats.usagePercent()),
                "activeUsers", stats.activeUsers(),
                "activeIps", stats.activeIps(),
                "bannedIps", securityMonitor.getBannedIps().size()
        ));
    }

    /**
     * 获取封禁 IP 列表
     */
    @GetMapping("/banned")
    public ResponseEntity<?> getBannedIps(
            @RequestAttribute("currentRole") String role) {
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        return ResponseEntity.ok(securityMonitor.getBannedIps());
    }

    /**
     * 手动封禁 IP
     */
    @PostMapping("/ban")
    public ResponseEntity<Map<String, Object>> banIp(
            @RequestAttribute("currentRole") String role,
            @RequestBody Map<String, String> request) {
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        String ip = request.get("ip");
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ip 不能为空"));
        }
        securityMonitor.banIp(ip);
        return ResponseEntity.ok(Map.of("message", "IP 已封禁", "ip", ip));
    }

    /**
     * 手动解封 IP
     */
    @PostMapping("/unban")
    public ResponseEntity<Map<String, Object>> unbanIp(
            @RequestAttribute("currentRole") String role,
            @RequestBody Map<String, String> request) {
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        String ip = request.get("ip");
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ip 不能为空"));
        }
        securityMonitor.unbanIp(ip);
        return ResponseEntity.ok(Map.of("message", "IP 已解封", "ip", ip));
    }
}
