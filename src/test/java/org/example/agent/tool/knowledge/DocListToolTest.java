package org.example.agent.tool.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocListTool 测试
 * 验证文档列表功能
 */
class DocListToolTest {

    private DocListTool docListTool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        docListTool = new DocListTool();
        setField(docListTool, "knowledgeBasePath", tempDir.toString());
    }

    @Test
    void listKnowledgeBaseDocuments_shouldListMdFiles() throws IOException {
        Files.writeString(tempDir.resolve("doc1.md"), "# 文档1\n\n内容");
        Files.writeString(tempDir.resolve("doc2.md"), "# 文档2\n\n内容");

        String result = docListTool.listKnowledgeBaseDocuments();

        assertTrue(result.contains("doc1.md"), "Should list doc1");
        assertTrue(result.contains("doc2.md"), "Should list doc2");
        assertTrue(result.contains("共 2 个文档"), "Should show count");
    }

    @Test
    void listKnowledgeBaseDocuments_shouldExtractTitle() throws IOException {
        Files.writeString(tempDir.resolve("cpu.md"), "# CPU 高负载处理方案\n\n内容");

        String result = docListTool.listKnowledgeBaseDocuments();

        assertTrue(result.contains("CPU 高负载处理方案"), "Should extract title from first heading");
    }

    @Test
    void listKnowledgeBaseDocuments_shouldHandleEmptyDirectory() {
        String result = docListTool.listKnowledgeBaseDocuments();

        assertTrue(result.contains("暂无文档"), "Should show empty message");
        assertTrue(result.contains("共 0 个文档"), "Should show zero count");
    }

    @Test
    void listKnowledgeBaseDocuments_shouldHandleNonExistentDirectory() {
        setField(docListTool, "knowledgeBasePath", "/non/existent/path");

        String result = docListTool.listKnowledgeBaseDocuments();

        assertTrue(result.contains("不存在"), "Should report non-existent directory");
    }

    @Test
    void listKnowledgeBaseDocuments_shouldListTxtFiles() throws IOException {
        Files.writeString(tempDir.resolve("readme.txt"), "自述文件");

        String result = docListTool.listKnowledgeBaseDocuments();

        assertTrue(result.contains("readme.txt"), "Should list .txt files");
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
