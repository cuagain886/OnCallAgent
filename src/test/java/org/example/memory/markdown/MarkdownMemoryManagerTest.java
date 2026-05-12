package org.example.memory.markdown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarkdownMemoryManager 单元测试
 */
class MarkdownMemoryManagerTest {

    @TempDir
    Path tempDir;

    private MemoryFileWriter fileWriter;
    private MemoryIndexManager indexManager;
    private MarkdownMemoryManager manager;

    @BeforeEach
    void setUp() {
        fileWriter = new MemoryFileWriter();
        indexManager = new MemoryIndexManager(fileWriter);
        manager = new MarkdownMemoryManager(fileWriter, indexManager);

        // 使用反射设置私有字段
        try {
            var rootField = MarkdownMemoryManager.class.getDeclaredField("memoryRoot");
            rootField.setAccessible(true);
            rootField.set(manager, tempDir);

            var enabledField = MarkdownMemoryManager.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(manager, true);

            // 手动创建目录结构
            Files.createDirectories(tempDir);
            for (MemoryType type : MemoryType.values()) {
                Files.createDirectories(tempDir.resolve(type.getDirectory()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSaveMemory() {
        // Given
        String fileName = "test_memory";
        String name = "Test Memory";
        String description = "A test memory for unit testing";
        String content = "## Test Content\nThis is a test.";

        // When
        manager.saveMemory(MemoryType.USER, fileName, name, description, content);

        // Then
        Path filePath = tempDir.resolve("user").resolve("test_memory.md");
        assertTrue(Files.exists(filePath));

        String fileContent = fileWriter.readFullContent(filePath);
        assertTrue(fileContent.contains("name: Test Memory"));
        assertTrue(fileContent.contains("description: A test memory for unit testing"));
        assertTrue(fileContent.contains("type: user"));
        assertTrue(fileContent.contains("## Test Content"));
    }

    @Test
    void testSaveMemoryCreatesIndex() {
        // Given
        manager.saveMemory(MemoryType.USER, "user_pref", "User Preferences", "User preferences", "content");
        manager.saveMemory(MemoryType.PROJECT, "proj_arch", "Project Architecture", "Architecture", "content");

        // When
        String index = manager.getIndexContent();

        // Then
        assertNotNull(index);
        // 检查是否包含记忆条目（使用英文描述，避免编码问题）
        assertTrue(index.contains("[User Preferences]"));
        assertTrue(index.contains("user_pref.md"));
        assertTrue(index.contains("User preferences"));
        assertTrue(index.contains("[Project Architecture]"));
        assertTrue(index.contains("proj_arch.md"));
        assertTrue(index.contains("Architecture"));
        // 检查是否有分组标题
        assertTrue(index.contains("##"));
    }

    @Test
    void testReadMemory() {
        // Given
        String content = "## Test\nHello World";
        manager.saveMemory(MemoryType.FEEDBACK, "test_fb", "Test Feedback", "Feedback", content);

        // When
        String readContent = manager.readMemory(MemoryType.FEEDBACK, "test_fb");

        // Then
        assertNotNull(readContent);
        assertTrue(readContent.contains("## Test"));
        assertTrue(readContent.contains("Hello World"));
        assertFalse(readContent.contains("---"));  // 不应包含 frontmatter
    }

    @Test
    void testUpdateMemory() {
        // Given
        manager.saveMemory(MemoryType.USER, "user_style", "Work Style", "Style", "Old content");

        // When
        manager.updateMemory(MemoryType.USER, "user_style", "New content");

        // Then
        String content = manager.readMemory(MemoryType.USER, "user_style");
        assertEquals("New content", content);
    }

    @Test
    void testAppendMemory() {
        // Given
        manager.saveMemory(MemoryType.FEEDBACK, "testing", "Testing", "Testing feedback", "## Section 1");

        // When
        manager.appendMemory(MemoryType.FEEDBACK, "testing", "Testing", "Testing feedback", "## Section 2");

        // Then
        String content = manager.readMemory(MemoryType.FEEDBACK, "testing");
        assertTrue(content.contains("## Section 1"));
        assertTrue(content.contains("## Section 2"));
    }

    @Test
    void testDeleteMemory() {
        // Given
        manager.saveMemory(MemoryType.REFERENCE, "docs", "Docs", "Documentation", "content");
        assertTrue(manager.memoryExists(MemoryType.REFERENCE, "docs"));

        // When
        boolean deleted = manager.deleteMemory(MemoryType.REFERENCE, "docs");

        // Then
        assertTrue(deleted);
        assertFalse(manager.memoryExists(MemoryType.REFERENCE, "docs"));
    }

    @Test
    void testMemoryExists() {
        // Given
        assertFalse(manager.memoryExists(MemoryType.USER, "nonexistent"));

        // When
        manager.saveMemory(MemoryType.USER, "existing", "Existing", "Exists", "content");

        // Then
        assertTrue(manager.memoryExists(MemoryType.USER, "existing"));
    }

    @Test
    void testGetFilePath() {
        // When
        Path filePath = manager.getFilePath(MemoryType.USER, "test");

        // Then
        assertEquals(tempDir.resolve("user").resolve("test.md"), filePath);
    }

    @Test
    void testGetFilePathWithExtension() {
        // When
        Path filePath = manager.getFilePath(MemoryType.USER, "test.md");

        // Then
        assertEquals(tempDir.resolve("user").resolve("test.md"), filePath);
    }

    @Test
    void testValidateIndex() {
        // Given - 空索引应该有效
        assertTrue(manager.validateIndex());

        // When - 添加一些记忆
        manager.saveMemory(MemoryType.USER, "test1", "Test 1", "Desc 1", "content");
        manager.saveMemory(MemoryType.USER, "test2", "Test 2", "Desc 2", "content");

        // Then - 应该仍然有效
        assertTrue(manager.validateIndex());
    }
}
