package org.example.agent.tool.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * 知识库文档列表工具
 * 列出当前知识库中的所有文档
 */
@Component
public class DocListTool {

    private static final Logger logger = LoggerFactory.getLogger(DocListTool.class);

    @Value("${file.upload.path}")
    private String knowledgeBasePath;

    @Tool(description = "List all documents currently in the knowledge base. " +
            "Returns document filenames and their first heading as title. " +
            "Use this to understand what documents already exist before creating or updating.")
    public String listKnowledgeBaseDocuments() {
        try {
            Path dir = Paths.get(knowledgeBasePath);
            if (!Files.exists(dir)) {
                return "知识库目录不存在: " + knowledgeBasePath;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("知识库文档列表：\n\n");

            int count = 0;
            try (Stream<Path> files = Files.walk(dir)) {
                List<Path> mdFiles = files
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".txt"))
                        .sorted()
                        .toList();

                for (Path file : mdFiles) {
                    count++;
                    String filename = dir.relativize(file).toString().replace("\\", "/");
                    String title = extractTitle(file);
                    sb.append(count).append(". ").append(filename);
                    if (title != null) {
                        sb.append(" — ").append(title);
                    }
                    sb.append("\n");
                }
            }

            if (count == 0) {
                sb.append("（知识库中暂无文档）\n");
            }
            sb.append("\n共 ").append(count).append(" 个文档");
            return sb.toString();
        } catch (Exception e) {
            logger.error("DocListTool 列出文档失败", e);
            return "列出文档时发生错误: " + e.getMessage();
        }
    }

    private String extractTitle(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return trimmed.substring(2).trim();
                }
            }
        } catch (IOException e) {
            logger.debug("读取文件标题失败: {}", file);
        }
        return null;
    }
}
