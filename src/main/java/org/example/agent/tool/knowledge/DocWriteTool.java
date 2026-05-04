package org.example.agent.tool.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 知识库文档写入工具
 * 支持创建新文档和更新已有文档
 */
@Component
public class DocWriteTool {

    private static final Logger logger = LoggerFactory.getLogger(DocWriteTool.class);

    @Value("${file.upload.path}")
    private String knowledgeBasePath;

    @Tool(description = "Write a new Markdown document to the knowledge base. " +
            "The filename should follow the pattern: {category_snake_case}.md. " +
            "Returns success/failure status.")
    public String writeNewDocument(
            @ToolParam(description = "The filename (e.g., 'network_dns_failure.md')") String filename,
            @ToolParam(description = "The full Markdown content") String content) {
        try {
            Path dir = Paths.get(knowledgeBasePath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            Path filePath = dir.resolve(filename);
            if (Files.exists(filePath)) {
                return "文件已存在: " + filename + "，请使用 updateDocument 更新。";
            }

            Files.writeString(filePath, content);
            logger.info("新文档已写入: {}", filePath);
            return "成功创建文档: " + filename + "（" + content.length() + " 字符）";
        } catch (Exception e) {
            logger.error("DocWriteTool 写入失败", e);
            return "写入文档失败: " + e.getMessage();
        }
    }

    @Tool(description = "Update an existing document in the knowledge base. " +
            "Replaces the entire content with the new version.")
    public String updateDocument(
            @ToolParam(description = "The filename to update") String filename,
            @ToolParam(description = "The new full Markdown content") String content) {
        try {
            Path filePath = Paths.get(knowledgeBasePath).resolve(filename);
            if (!Files.exists(filePath)) {
                return "文件不存在: " + filename + "，请使用 writeNewDocument 创建。";
            }

            // 备份原文件
            Path backupPath = filePath.resolveSibling(filename + ".bak");
            Files.copy(filePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            Files.writeString(filePath, content);
            logger.info("文档已更新: {}（备份: {}）", filePath, backupPath);
            return "成功更新文档: " + filename + "（" + content.length() + " 字符，已备份原文件）";
        } catch (Exception e) {
            logger.error("DocWriteTool 更新失败", e);
            return "更新文档失败: " + e.getMessage();
        }
    }
}
