package org.example.memory.markdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MEMORY.md 索引管理器
 *
 * 负责维护一级索引文件，格式要求：
 * - 每行一个条目：- [显示名称](相对路径) — 一句话摘要
 * - 整个文件限制在 200 行、25KB 以内
 * - 始终加载到 Agent 上下文
 */
@Component
public class MemoryIndexManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryIndexManager.class);

    private static final int MAX_LINES = 200;
    private static final long MAX_SIZE_KB = 25;
    private static final int MAX_DESCRIPTION_LENGTH = 150;

    private final MemoryFileWriter fileWriter;

    public MemoryIndexManager(MemoryFileWriter fileWriter) {
        this.fileWriter = fileWriter;
    }

    /**
     * 重建 MEMORY.md 索引
     * 扫描所有记忆文件，重新生成索引
     */
    public void rebuildIndex(Path memoryRoot) {
        Path indexPath = memoryRoot.resolve("MEMORY.md");
        List<IndexEntry> entries = new ArrayList<>();

        // 扫描所有记忆文件的 frontmatter
        try (Stream<Path> paths = Files.walk(memoryRoot, 2)) {
            paths.filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equals("MEMORY.md"))
                    .forEach(path -> {
                        try {
                            MemoryFrontmatter fm = fileWriter.parseFrontmatter(path);
                            if (fm != null) {
                                Path relativePath = memoryRoot.relativize(path);
                                entries.add(new IndexEntry(
                                        fm.getName(),
                                        relativePath,
                                        fm.getDescription(),
                                        fm.getType(),
                                        fm.getUpdated()
                                ));
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse frontmatter: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk memory directory", e);
            return;
        }

        // 按类型和更新时间排序
        entries.sort(Comparator
                .comparing(IndexEntry::getType)
                .thenComparing(IndexEntry::getUpdated, Comparator.reverseOrder()));

        // 生成索引内容
        String indexContent = generateIndexContent(entries);

        // 写入文件
        try {
            Files.createDirectories(memoryRoot);
            Files.writeString(indexPath, indexContent);
            log.info("Rebuilt MEMORY.md index with {} entries", entries.size());
        } catch (IOException e) {
            log.error("Failed to write MEMORY.md", e);
        }
    }

    /**
     * 生成索引内容
     */
    private String generateIndexContent(List<IndexEntry> entries) {
        StringBuilder sb = new StringBuilder();

        // 按类型分组
        Map<MemoryType, List<IndexEntry>> grouped = entries.stream()
                .collect(Collectors.groupingBy(IndexEntry::getType));

        // 生成各类型索引
        for (MemoryType type : MemoryType.values()) {
            List<IndexEntry> typeEntries = grouped.get(type);
            if (typeEntries != null && !typeEntries.isEmpty()) {
                sb.append("## ").append(type.getDescription()).append("\n\n");
                for (IndexEntry entry : typeEntries) {
                    sb.append(String.format("- [%s](%s) — %s\n",
                            entry.getName(),
                            entry.getRelativePath(),
                            truncate(entry.getDescription(), MAX_DESCRIPTION_LENGTH)
                    ));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * 检查索引是否符合限制
     */
    public boolean validateIndex(Path memoryRoot) {
        Path indexPath = memoryRoot.resolve("MEMORY.md");
        if (!Files.exists(indexPath)) {
            return true;  // 不存在视为有效
        }

        try {
            String content = Files.readString(indexPath);
            long lineCount = content.lines().count();
            long sizeKB = Files.size(indexPath) / 1024;

            if (lineCount > MAX_LINES) {
                log.warn("MEMORY.md exceeds {} lines (current: {})", MAX_LINES, lineCount);
                return false;
            }

            if (sizeKB > MAX_SIZE_KB) {
                log.warn("MEMORY.md exceeds {}KB (current: {}KB)", MAX_SIZE_KB, sizeKB);
                return false;
            }

            return true;
        } catch (IOException e) {
            log.error("Failed to validate MEMORY.md", e);
            return false;
        }
    }

    /**
     * 读取索引内容
     */
    public String readIndex(Path memoryRoot) {
        Path indexPath = memoryRoot.resolve("MEMORY.md");
        return fileWriter.readFullContent(indexPath);
    }

    /**
     * 索引条目内部类
     */
    private static class IndexEntry {
        private final String name;
        private final Path relativePath;
        private final String description;
        private final MemoryType type;
        private final java.time.LocalDate updated;

        public IndexEntry(String name, Path relativePath, String description,
                          MemoryType type, java.time.LocalDate updated) {
            this.name = name;
            this.relativePath = relativePath;
            this.description = description;
            this.type = type;
            this.updated = updated;
        }

        public String getName() {
            return name;
        }

        public Path getRelativePath() {
            return relativePath;
        }

        public String getDescription() {
            return description;
        }

        public MemoryType getType() {
            return type;
        }

        public java.time.LocalDate getUpdated() {
            return updated;
        }
    }
}
