package org.example.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 输入校验工具测试
 */
class InputValidatorTest {

    private InputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InputValidator();
    }

    // ==================== 问题校验测试 ====================

    @Test
    void validateQuestion_shouldAcceptValidQuestion() {
        InputValidator.ValidationResult result = validator.validateQuestion("CPU 使用率过高怎么办？");
        assertTrue(result.valid());
    }

    @Test
    void validateQuestion_shouldRejectNull() {
        InputValidator.ValidationResult result = validator.validateQuestion(null);
        assertFalse(result.valid());
        assertNotNull(result.error());
    }

    @Test
    void validateQuestion_shouldRejectBlank() {
        InputValidator.ValidationResult result = validator.validateQuestion("   ");
        assertFalse(result.valid());
    }

    @Test
    void validateQuestion_shouldRejectTooLong() {
        String longQuestion = "a".repeat(2001);
        InputValidator.ValidationResult result = validator.validateQuestion(longQuestion);
        assertFalse(result.valid());
        assertTrue(result.error().contains("2000"));
    }

    @Test
    void validateQuestion_shouldAcceptMaxLength() {
        String maxQuestion = "a".repeat(2000);
        InputValidator.ValidationResult result = validator.validateQuestion(maxQuestion);
        assertTrue(result.valid());
    }

    // ==================== SessionId 校验测试 ====================

    @Test
    void validateSessionId_shouldAcceptValidId() {
        assertTrue(validator.validateSessionId("session-123").valid());
        assertTrue(validator.validateSessionId("abc_def-456").valid());
    }

    @Test
    void validateSessionId_shouldAcceptNull() {
        assertTrue(validator.validateSessionId(null).valid(), "Null should be allowed (auto-generated)");
    }

    @Test
    void validateSessionId_shouldAcceptBlank() {
        assertTrue(validator.validateSessionId("").valid(), "Blank should be allowed");
    }

    @Test
    void validateSessionId_shouldRejectSpecialChars() {
        assertFalse(validator.validateSessionId("session@123").valid());
        assertFalse(validator.validateSessionId("session 123").valid());
        assertFalse(validator.validateSessionId("session/123").valid());
    }

    @Test
    void validateSessionId_shouldRejectTooLong() {
        String longId = "a".repeat(65);
        assertFalse(validator.validateSessionId(longId).valid());
    }

    @Test
    void validateSessionId_shouldAcceptMaxLength() {
        String maxId = "a".repeat(64);
        assertTrue(validator.validateSessionId(maxId).valid());
    }

    // ==================== 报告内容校验测试 ====================

    @Test
    void validateReportContent_shouldAcceptValid() {
        assertTrue(validator.validateReportContent("# 告警分析报告").valid());
    }

    @Test
    void validateReportContent_shouldRejectNull() {
        assertFalse(validator.validateReportContent(null).valid());
    }

    @Test
    void validateReportContent_shouldRejectTooLong() {
        String longContent = "a".repeat(50001);
        assertFalse(validator.validateReportContent(longContent).valid());
    }

    // ==================== 文件名校验测试 ====================

    @Test
    void validateFilename_shouldAcceptValid() {
        assertTrue(validator.validateFilename("test.md").valid());
        assertTrue(validator.validateFilename("CPU_高负载.md").valid());
    }

    @Test
    void validateFilename_shouldRejectNull() {
        assertFalse(validator.validateFilename(null).valid());
    }

    @Test
    void validateFilename_shouldRejectPathTraversal() {
        assertFalse(validator.validateFilename("../etc/passwd").valid());
        assertFalse(validator.validateFilename("..\\windows\\system32").valid());
    }

    @Test
    void validateFilename_shouldRejectSlashes() {
        assertFalse(validator.validateFilename("dir/file.md").valid());
        assertFalse(validator.validateFilename("dir\\file.md").valid());
    }

    @Test
    void validateFilename_shouldRejectTooLong() {
        String longName = "a".repeat(256) + ".md";
        assertFalse(validator.validateFilename(longName).valid());
    }

    // ==================== 文件大小校验测试 ====================

    @Test
    void validateFileSize_shouldAcceptValid() {
        assertTrue(validator.validateFileSize(1024).valid());
    }

    @Test
    void validateFileSize_shouldRejectTooLarge() {
        assertFalse(validator.validateFileSize(6 * 1024 * 1024).valid());
    }

    // ==================== 文件扩展名校验测试 ====================

    @Test
    void validateFileExtension_shouldAcceptAllowed() {
        assertTrue(validator.validateFileExtension("test.md", "txt,md").valid());
        assertTrue(validator.validateFileExtension("test.txt", "txt,md").valid());
    }

    @Test
    void validateFileExtension_shouldRejectDisallowed() {
        assertFalse(validator.validateFileExtension("test.exe", "txt,md").valid());
        assertFalse(validator.validateFileExtension("test.pdf", "txt,md").valid());
    }

    @Test
    void validateFileExtension_shouldRejectNoExtension() {
        assertFalse(validator.validateFileExtension("Makefile", "txt,md").valid());
    }

    // ==================== 文件名清理测试 ====================

    @Test
    void sanitizeFilename_shouldRemovePath() {
        assertEquals("file.md", validator.sanitizeFilename("/path/to/file.md"));
        assertEquals("file.md", validator.sanitizeFilename("C:\\path\\file.md"));
    }

    @Test
    void sanitizeFilename_shouldReplaceDangerousChars() {
        String result = validator.sanitizeFilename("file<script>.md");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
    }

    @Test
    void sanitizeFilename_shouldPreserveChinese() {
        assertEquals("CPU_高负载.md", validator.sanitizeFilename("CPU_高负载.md"));
    }

    @Test
    void sanitizeFilename_shouldHandleNull() {
        assertNull(validator.sanitizeFilename(null));
    }
}
