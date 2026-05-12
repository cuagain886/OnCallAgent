package org.example.context;

import org.example.memory.markdown.MarkdownMemoryManager;
import org.example.memory.markdown.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserProfileInjector 单元测试
 */
class UserProfileInjectorTest {

    @TempDir
    Path tempDir;

    private UserProfileInjector injector;
    private MarkdownMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        // 创建真实的 MarkdownMemoryManager
        memoryManager = new MarkdownMemoryManager(
                new org.example.memory.markdown.MemoryFileWriter(),
                new org.example.memory.markdown.MemoryIndexManager(
                        new org.example.memory.markdown.MemoryFileWriter()
                )
        );

        // 使用反射设置私有字段
        try {
            var rootField = MarkdownMemoryManager.class.getDeclaredField("memoryRoot");
            rootField.setAccessible(true);
            rootField.set(memoryManager, tempDir);

            var enabledField = MarkdownMemoryManager.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(memoryManager, true);

            // 手动创建目录结构
            java.nio.file.Files.createDirectories(tempDir);
            for (MemoryType type : MemoryType.values()) {
                java.nio.file.Files.createDirectories(tempDir.resolve(type.getDirectory()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        injector = new UserProfileInjector();

        // 使用反射设置私有字段
        try {
            var memoryManagerField = UserProfileInjector.class.getDeclaredField("memoryManager");
            memoryManagerField.setAccessible(true);
            memoryManagerField.set(injector, memoryManager);

            var enabledField = UserProfileInjector.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(injector, true);

            var maxLengthField = UserProfileInjector.class.getDeclaredField("maxLength");
            maxLengthField.setAccessible(true);
            maxLengthField.set(injector, 500);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testInjectWithExistingProfile() {
        // Given - 创建用户画像
        memoryManager.saveMemory(MemoryType.USER, "user_test",
                "User Profile", "用户画像",
                "## 偏好\n- 喜欢详细回答\n- 使用中文");

        // When
        ContextSection result = injector.inject("test");

        // Then
        assertNotNull(result);
        assertEquals("user_profile", result.getId());
        assertEquals(ContextSection.SectionType.USER_PROFILE, result.getType());
        assertTrue(result.getContent().contains("用户画像"));
        assertTrue(result.getContent().contains("喜欢详细回答"));
    }

    @Test
    void testInjectWithNonExistingProfile() {
        // When - 用户不存在
        ContextSection result = injector.inject("nonexistent");

        // Then
        assertNotNull(result);
        assertEquals("user_profile", result.getId());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void testInjectWithLongProfile() {
        // Given - 创建超长用户画像
        String longProfile = "这是一个很长的用户画像。".repeat(100);
        memoryManager.saveMemory(MemoryType.USER, "user_long",
                "Long Profile", "长画像", longProfile);

        // When
        ContextSection result = injector.inject("long");

        // Then
        assertNotNull(result);
        assertTrue(result.getContent().length() <= 600); // 500 + 格式化开销
    }

    @Test
    void testHasProfileWithExisting() {
        // Given
        memoryManager.saveMemory(MemoryType.USER, "user_exists",
                "Exists", "存在", "内容");

        // When & Then
        assertTrue(injector.hasProfile("exists"));
    }

    @Test
    void testHasProfileWithNonExisting() {
        // When & Then
        assertFalse(injector.hasProfile("nonexistent"));
    }

    @Test
    void testGetProfileSummary() {
        // Given
        memoryManager.saveMemory(MemoryType.USER, "user_summary",
                "Summary", "摘要测试", "## 偏好\n- 喜欢简洁回答");

        // When
        String summary = injector.getProfileSummary("summary");

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("偏好"));
    }

    @Test
    void testGetProfileSummaryNonExisting() {
        // When
        String summary = injector.getProfileSummary("nonexistent");

        // Then
        assertNull(summary);
    }

    @Test
    void testDisabledInjector() throws Exception {
        // Given
        var enabledField = UserProfileInjector.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(injector, false);

        // When
        ContextSection result = injector.inject("test");

        // Then
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
    }
}
