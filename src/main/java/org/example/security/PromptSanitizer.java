package org.example.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 注入防护
 * 检测用户输入中是否包含试图绕过系统指令的恶意内容
 */
@Component
public class PromptSanitizer {

    // 中英文注入模式
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)忽略.*(?:之前|以上|所有).*(?:指令|规则|指示)"),
            Pattern.compile("(?i)ignore.*(?:previous|above|all).*(?:instructions|rules)"),
            Pattern.compile("(?i)system.*prompt"),
            Pattern.compile("(?i)api.*key"),
            Pattern.compile("(?i)你的.*(?:密钥|密码|key|secret)"),
            Pattern.compile("(?i)输出.*(?:密码|密钥|key|secret|prompt)"),
            Pattern.compile("(?i)你现在是"),
            Pattern.compile("(?i)pretend.*(?:you|that).*(?:are|is)"),
            Pattern.compile("(?i)你是一个没有"),
            Pattern.compile("(?i)do anything.*(?:now|now)"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("(?i)DAN.*mode"),
            Pattern.compile("(?i)developer.*mode"),
            Pattern.compile("(?i)忘记.*(?:之前|以上|所有).*(?:指令|规则)")
    );

    // 可疑内容模式（不一定是攻击，但需要记录）
    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("(?i)password"),
            Pattern.compile("(?i)secret"),
            Pattern.compile("(?i)token"),
            Pattern.compile("(?i)credential"),
            Pattern.compile("(?i)注入"),
            Pattern.compile("(?i)injection"),
            Pattern.compile("(?i)hack"),
            Pattern.compile("(?i)exploit")
    );

    /**
     * 检测是否包含注入攻击
     */
    public InjectionResult detect(String input) {
        if (input == null || input.isBlank()) {
            return InjectionResult.safe(input);
        }

        // 检查注入模式
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return InjectionResult.blocked(input, "检测到 Prompt 注入尝试: " + pattern.pattern());
            }
        }

        // 检查可疑模式（不阻止，仅标记）
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return InjectionResult.suspicious(input, "包含可疑关键词: " + pattern.pattern());
            }
        }

        return InjectionResult.safe(input);
    }

    /**
     * 清理输入（转义特殊字符）
     */
    public String sanitize(String input) {
        if (input == null) return null;
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 截断输入到指定长度
     */
    public String truncate(String input, int maxLength) {
        if (input == null) return null;
        if (input.length() <= maxLength) return input;
        return input.substring(0, maxLength);
    }

    /**
     * 注入检测结果
     */
    public record InjectionResult(String input, String level, String reason) {
        public static InjectionResult safe(String input) {
            return new InjectionResult(input, "SAFE", null);
        }
        public static InjectionResult suspicious(String input, String reason) {
            return new InjectionResult(input, "SUSPICIOUS", reason);
        }
        public static InjectionResult blocked(String input, String reason) {
            return new InjectionResult(input, "BLOCKED", reason);
        }
        public boolean isBlocked() { return "BLOCKED".equals(level); }
        public boolean isSuspicious() { return "SUSPICIOUS".equals(level); }
    }
}
