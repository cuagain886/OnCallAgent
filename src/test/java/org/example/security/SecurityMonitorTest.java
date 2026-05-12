package org.example.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全监控测试
 */
class SecurityMonitorTest {

    private SecurityMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new SecurityMonitor();
    }

    // ==================== IP 封禁测试 ====================

    @Test
    void banIp_shouldBanIp() {
        monitor.banIp("10.0.0.1");
        assertTrue(monitor.isBanned("10.0.0.1"));
    }

    @Test
    void unbanIp_shouldUnbanIp() {
        monitor.banIp("10.0.0.1");
        monitor.unbanIp("10.0.0.1");
        assertFalse(monitor.isBanned("10.0.0.1"));
    }

    @Test
    void isBanned_shouldReturnFalseForUnknownIp() {
        assertFalse(monitor.isBanned("10.0.0.99"));
    }

    @Test
    void getBannedIps_shouldReturnBannedIps() {
        monitor.banIp("10.0.0.1");
        monitor.banIp("10.0.0.2");

        var banned = monitor.getBannedIps();
        assertEquals(2, banned.size());
        assertTrue(banned.containsKey("10.0.0.1"));
        assertTrue(banned.containsKey("10.0.0.2"));
    }

    // ==================== 调用记录测试 ====================

    @Test
    void recordCall_shouldNotThrow() {
        assertDoesNotThrow(() -> {
            monitor.recordCall("10.0.0.1");
            monitor.recordCall("10.0.0.1");
            monitor.recordCall("10.0.0.2");
        });
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
