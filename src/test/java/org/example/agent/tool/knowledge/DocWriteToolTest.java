package org.example.agent.tool.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocWriteTool 测试
 * 验证文档写入和更新功能
 */
class DocWriteToolTest {

    private DocWriteTool docWriteTool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        docWriteTool = new DocWriteTool();
        setField(docWriteTool, "knowledgeBasePath", tempDir.toString());
    }

    @Test
    void writeNewDocument_shouldCreateFile() {
        String result = docWriteTool.writeNewDocument("test.md", "# 测试文档\n\n内容");

        assertTrue(result.contains("成功"), "Should succeed");
        assertTrue(Files.exists(tempDir.resolve("test.md")), "File should exist");
    }

    @Test
    void writeNewDocument_shouldRejectExistingFile() throws IOException {
        Files.writeString(tempDir.resolve("existing.md"), "已有内容");

        String result = docWriteTool.writeNewDocument("existing.md", "新内容");

        assertTrue(result.contains("已存在"), "Should reject existing file");
    }

    @Test
    void writeNewDocument_shouldCreateDirectoryIfMissing() {
        Path subDir = tempDir.resolve("subdir");
        setField(docWriteTool, "knowledgeBasePath", subDir.toString());

        String result = docWriteTool.writeNewDocument("test.md", "# 测试");

        assertTrue(result.contains("成功"), "Should create directory and file");
        assertTrue(Files.exists(subDir.resolve("test.md")));
    }

    @Test
    void updateDocument_shouldUpdateExistingFile() throws IOException {
        Files.writeString(tempDir.resolve("doc.md"), "旧内容");

        String result = docWriteTool.updateDocument("doc.md", "新内容");

        assertTrue(result.contains("成功"), "Should succeed");
        assertEquals("新内容", Files.readString(tempDir.resolve("doc.md")));
    }

    @Test
    void updateDocument_shouldCreateBackup() throws IOException {
        Files.writeString(tempDir.resolve("doc.md"), "旧内容");

        String result = docWriteTool.updateDocument("doc.md", "新内容");

        assertTrue(result.contains("成功"), "Update should succeed: " + result);
        // 备份文件名格式为 {filename}.bak
        Path backupPath = tempDir.resolve("doc.md.bak");
        assertTrue(Files.exists(backupPath), "Backup should be created at " + backupPath);
        assertEquals("旧内容", Files.readString(backupPath));
    }

    @Test
    void updateDocument_shouldRejectNonExistingFile() {
        String result = docWriteTool.updateDocument("nonexistent.md", "内容");

        assertTrue(result.contains("不存在"), "Should reject non-existing file");
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
