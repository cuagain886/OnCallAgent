package org.example.context;

import org.example.memory.markdown.MarkdownMemoryManager;
import org.example.memory.markdown.MemoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 用户画像注入器
 *
 * 从 Markdown 记忆系统中读取用户画像，注入到上下文中。
 * 支持个性化响应，根据用户偏好调整回答风格。
 */
@Component
public class UserProfileInjector {

    private static final Logger log = LoggerFactory.getLogger(UserProfileInjector.class);

    @Autowired(required = false)
    private MarkdownMemoryManager memoryManager;

    @Value("${context.user-profile.enabled:true}")
    private boolean enabled;

    @Value("${context.user-profile.max-length:500}")
    private int maxLength;

    /**
     * 注入用户画像
     *
     * @param userId 用户 ID
     * @return 用户画像上下文片段
     */
    public ContextSection inject(String userId) {
        if (!enabled || memoryManager == null || !memoryManager.isEnabled()) {
            return buildEmptyProfile();
        }

        try {
            // 从 Markdown 记忆中读取用户画像
            String profile = memoryManager.readMemory(MemoryType.USER, "user_" + userId);

            if (profile == null || profile.isEmpty()) {
                log.debug("未找到用户画像: {}", userId);
                return buildEmptyProfile();
            }

            // 截断到最大长度
            if (profile.length() > maxLength) {
                profile = profile.substring(0, maxLength) + "...";
            }

            // 格式化用户画像
            String formatted = formatUserProfile(profile);

            return ContextSection.builder()
                    .id("user_profile")
                    .content(formatted)
                    .type(ContextSection.SectionType.USER_PROFILE)
                    .priority(5)
                    .weight(0.05)
                    .build();
        } catch (Exception e) {
            log.warn("读取用户画像失败: {}", userId, e);
            return buildEmptyProfile();
        }
    }

    /**
     * 格式化用户画像
     */
    private String formatUserProfile(String profile) {
        return """
                ## 用户画像
                %s
                """.formatted(profile);
    }

    /**
     * 构建空的用户画像
     */
    private ContextSection buildEmptyProfile() {
        return ContextSection.builder()
                .id("user_profile")
                .content("")
                .type(ContextSection.SectionType.USER_PROFILE)
                .priority(5)
                .weight(0.05)
                .build();
    }

    /**
     * 检查用户画像是否存在
     */
    public boolean hasProfile(String userId) {
        if (!enabled || memoryManager == null || !memoryManager.isEnabled()) {
            return false;
        }

        return memoryManager.memoryExists(MemoryType.USER, "user_" + userId);
    }

    /**
     * 获取用户画像摘要
     */
    public String getProfileSummary(String userId) {
        if (!enabled || memoryManager == null || !memoryManager.isEnabled()) {
            return null;
        }

        try {
            String profile = memoryManager.readMemory(MemoryType.USER, "user_" + userId);
            if (profile == null || profile.isEmpty()) {
                return null;
            }

            // 提取前 100 个字符作为摘要
            if (profile.length() <= 100) {
                return profile;
            }
            return profile.substring(0, 100) + "...";
        } catch (Exception e) {
            log.warn("获取用户画像摘要失败: {}", userId, e);
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
