package org.example.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输入校验工具
 * 校验用户输入的长度、格式、安全性
 */
@Component
public class InputValidator {

    // SessionId 格式：字母、数字、下划线、短横线，1~64 字符
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,64}$");

    private static final int MAX_QUESTION_LENGTH = 2000;
    private static final int MAX_REPORT_LENGTH = 50000;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;  // 5MB

    /**
     * 校验聊天问题
     */
    public ValidationResult validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            return ValidationResult.fail("问题不能为空");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            return ValidationResult.fail("问题过长，最多 " + MAX_QUESTION_LENGTH + " 字符（当前 " + question.length() + "）");
        }
        return ValidationResult.ok();
    }

    /**
     * 校验 SessionId
     */
    public ValidationResult validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ValidationResult.ok();  // 允许为空（自动生成）
        }
        if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            return ValidationResult.fail("无效的 SessionId，仅允许字母、数字、下划线、短横线，1~64 字符");
        }
        return ValidationResult.ok();
    }

    /**
     * 校验报告内容
     */
    public ValidationResult validateReportContent(String content) {
        if (content == null || content.isBlank()) {
            return ValidationResult.fail("报告内容不能为空");
        }
        if (content.length() > MAX_REPORT_LENGTH) {
            return ValidationResult.fail("报告内容过长，最多 " + MAX_REPORT_LENGTH + " 字符");
        }
        return ValidationResult.ok();
    }

    /**
     * 校验文件名
     */
    public ValidationResult validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return ValidationResult.fail("文件名不能为空");
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            return ValidationResult.fail("文件名过长，最多 " + MAX_FILENAME_LENGTH + " 字符");
        }
        // 路径穿越检查
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ValidationResult.fail("文件名包含非法字符");
        }
        return ValidationResult.ok();
    }

    /**
     * 校验文件大小
     */
    public ValidationResult validateFileSize(long size) {
        if (size > MAX_FILE_SIZE) {
            return ValidationResult.fail("文件过大，最大 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }
        return ValidationResult.ok();
    }

    /**
     * 校验文件扩展名
     */
    public ValidationResult validateFileExtension(String filename, String allowedExtensions) {
        if (filename == null || allowedExtensions == null) {
            return ValidationResult.fail("文件名或允许的扩展名为空");
        }
        String ext = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = filename.substring(dotIndex + 1).toLowerCase();
        }
        if (ext.isEmpty()) {
            return ValidationResult.fail("文件缺少扩展名");
        }
        String[] allowed = allowedExtensions.split(",");
        for (String a : allowed) {
            if (a.trim().equalsIgnoreCase(ext)) {
                return ValidationResult.ok();
            }
        }
        return ValidationResult.fail("不支持的文件格式，仅支持: " + allowedExtensions);
    }

    /**
     * 清理文件名（防止路径穿越）
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) return null;
        // 只保留文件名部分，去除路径
        String name = filename;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        // 替换危险字符
        return name.replaceAll("[^a-zA-Z0-9_\\-.\\u4e00-\\u9fa5]", "_");
    }

    /**
     * 校验结果
     */
    public record ValidationResult(boolean valid, String error) {
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String error) { return new ValidationResult(false, error); }
    }
}
