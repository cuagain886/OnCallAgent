package org.example.memory.markdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Markdown 记忆文件读写器
 */
@Component
public class MemoryFileWriter {

    private static final Logger log = LoggerFactory.getLogger(MemoryFileWriter.class);

    /**
     * 创建新的记忆文件
     */
    public void create(Path file, MemoryFrontmatter frontmatter, String content) {
        try {
            // 确保父目录存在
            Files.createDirectories(file.getParent());

            // 构建完整内容
            String fullContent = frontmatter.toYaml() + "\n" + content;

            // 写入文件
            Files.writeString(file, fullContent);
            log.info("Created memory file: {}", file);
        } catch (IOException e) {
            log.error("Failed to create memory file: {}", file, e);
            throw new RuntimeException("Failed to create memory file", e);
        }
    }

    /**
     * 更新现有记忆文件（保留 frontmatter，更新内容）
     */
    public void updateOrCreate(Path file, String name, String description,
                               MemoryType type, String content) {
        if (Files.exists(file)) {
            update(file, content);
        } else {
            MemoryFrontmatter frontmatter = new MemoryFrontmatter(name, description, type);
            create(file, frontmatter, content);
        }
    }

    /**
     * 更新现有记忆文件的内容
     */
    public void update(Path file, String newContent) {
        try {
            // 读取现有内容
            String existingContent = Files.readString(file);

            // 提取 frontmatter
            String frontmatter = extractFrontmatter(existingContent);

            // 更新 updated 日期
            frontmatter = frontmatter.replaceAll("updated: \\d{4}-\\d{2}-\\d{2}",
                    "updated: " + LocalDate.now());

            // 构建完整内容
            String fullContent = frontmatter + "\n" + newContent;

            // 写入文件
            Files.writeString(file, fullContent);
            log.info("Updated memory file: {}", file);
        } catch (IOException e) {
            log.error("Failed to update memory file: {}", file, e);
            throw new RuntimeException("Failed to update memory file", e);
        }
    }

    /**
     * 追加内容到记忆文件
     */
    public void appendOrCreate(Path file, String name, String description,
                               MemoryType type, String section) {
        if (Files.exists(file)) {
            append(file, section);
        } else {
            MemoryFrontmatter frontmatter = new MemoryFrontmatter(name, description, type);
            create(file, frontmatter, section);
        }
    }

    /**
     * 追加内容到现有文件
     */
    public void append(Path file, String section) {
        try {
            // 读取现有内容
            String existingContent = Files.readString(file);

            // 提取 frontmatter 和 body
            String frontmatter = extractFrontmatter(existingContent);
            String body = extractBody(existingContent);

            // 更新 updated 日期
            frontmatter = frontmatter.replaceAll("updated: \\d{4}-\\d{2}-\\d{2}",
                    "updated: " + LocalDate.now());

            // 构建完整内容
            String fullContent = frontmatter + "\n" + body + "\n\n" + section;

            // 写入文件
            Files.writeString(file, fullContent);
            log.info("Appended to memory file: {}", file);
        } catch (IOException e) {
            log.error("Failed to append to memory file: {}", file, e);
            throw new RuntimeException("Failed to append to memory file", e);
        }
    }

    /**
     * 读取记忆文件的完整内容
     */
    public String readFullContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            log.error("Failed to read memory file: {}", file, e);
            return null;
        }
    }

    /**
     * 读取记忆文件的 body 内容（不含 frontmatter）
     */
    public String readBody(Path file) {
        try {
            String content = Files.readString(file);
            return extractBody(content);
        } catch (IOException e) {
            log.error("Failed to read memory file: {}", file, e);
            return null;
        }
    }

    /**
     * 解析 frontmatter
     */
    public MemoryFrontmatter parseFrontmatter(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return parseFrontmatterFromLines(lines, file);
        } catch (IOException e) {
            log.error("Failed to parse frontmatter: {}", file, e);
            return null;
        }
    }

    /**
     * 从行列表解析 frontmatter（只读前30行）
     */
    public MemoryFrontmatter parseFrontmatterFromLines(List<String> lines, Path filePath) {
        if (lines.isEmpty() || !lines.get(0).equals("---")) {
            return null;
        }

        MemoryFrontmatter fm = new MemoryFrontmatter();
        boolean inFrontmatter = false;
        int lineCount = 0;

        for (String line : lines) {
            lineCount++;
            if (lineCount > 30) break;  // 只读前30行

            if (line.equals("---")) {
                if (inFrontmatter) {
                    break;  // frontmatter 结束
                } else {
                    inFrontmatter = true;
                    continue;
                }
            }

            if (inFrontmatter) {
                parseYamlLine(line, fm);
            }
        }

        // 如果没有解析到 name，从文件路径提取
        if (fm.getName() == null && filePath != null) {
            fm.setName(MemoryFrontmatter.extractNameFromPath(filePath));
        }

        return fm;
    }

    /**
     * 解析 YAML 行
     */
    private void parseYamlLine(String line, MemoryFrontmatter fm) {
        if (line.startsWith("name:")) {
            fm.setName(line.substring(5).trim());
        } else if (line.startsWith("description:")) {
            fm.setDescription(line.substring(12).trim());
        } else if (line.startsWith("type:")) {
            fm.setType(MemoryType.fromString(line.substring(5).trim()));
        } else if (line.startsWith("created:")) {
            fm.setCreated(LocalDate.parse(line.substring(8).trim()));
        } else if (line.startsWith("updated:")) {
            fm.setUpdated(LocalDate.parse(line.substring(8).trim()));
        } else if (line.startsWith("tags:")) {
            String tagsStr = line.substring(5).trim();
            if (tagsStr.startsWith("[") && tagsStr.endsWith("]")) {
                tagsStr = tagsStr.substring(1, tagsStr.length() - 1);
                fm.setTags(List.of(tagsStr.split(",")));
            }
        }
    }

    /**
     * 提取 frontmatter 部分
     */
    private String extractFrontmatter(String content) {
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean inFrontmatter = false;

        for (String line : lines) {
            if (line.equals("---")) {
                if (inFrontmatter) {
                    sb.append(line).append("\n");
                    break;
                } else {
                    inFrontmatter = true;
                }
            }
            if (inFrontmatter) {
                sb.append(line).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 提取 body 部分（不含 frontmatter）
     */
    private String extractBody(String content) {
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean inFrontmatter = false;
        boolean pastFrontmatter = false;

        for (String line : lines) {
            if (line.equals("---")) {
                if (inFrontmatter) {
                    inFrontmatter = false;
                    pastFrontmatter = true;
                    continue;
                } else if (!pastFrontmatter) {
                    inFrontmatter = true;
                    continue;
                }
            }

            if (pastFrontmatter || !inFrontmatter) {
                sb.append(line).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * 检查文件是否存在
     */
    public boolean exists(Path file) {
        return Files.exists(file);
    }

    /**
     * 删除文件
     */
    public boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete memory file: {}", file, e);
            return false;
        }
    }
}
