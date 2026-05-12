package org.example.memory.markdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Frontmatter 扫描器
 *
 * 扫描记忆目录下所有 .md 文件，只读取前 30 行 frontmatter 内容。
 * 用于对话开始时的异步 Prefetch 流程。
 */
@Component
public class FrontmatterScanner {

    private static final Logger log = LoggerFactory.getLogger(FrontmatterScanner.class);

    private static final int MAX_FRONTMATTER_LINES = 30;

    private final MemoryFileWriter fileWriter;

    public FrontmatterScanner(MemoryFileWriter fileWriter) {
        this.fileWriter = fileWriter;
    }

    /**
     * 扫描指定目录下所有记忆文件的 frontmatter
     *
     * @param memoryRoot 记忆根目录
     * @return frontmatter 列表
     */
    public List<ScannedMemory> scanAll(Path memoryRoot) {
        List<ScannedMemory> results = new ArrayList<>();

        if (!Files.exists(memoryRoot)) {
            log.warn("Memory root directory does not exist: {}", memoryRoot);
            return results;
        }

        try (Stream<Path> paths = Files.walk(memoryRoot, 2)) {
            paths.filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equals("MEMORY.md"))
                    .forEach(path -> {
                        try {
                            ScannedMemory scanned = scanFile(path, memoryRoot);
                            if (scanned != null) {
                                results.add(scanned);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to scan file: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk memory directory: {}", memoryRoot, e);
        }

        log.debug("Scanned {} memory files", results.size());
        return results;
    }

    /**
     * 扫描单个文件的 frontmatter
     *
     * @param file       文件路径
     * @param memoryRoot 记忆根目录
     * @return 扫描结果，如果解析失败返回 null
     */
    public ScannedMemory scanFile(Path file, Path memoryRoot) {
        try {
            List<String> lines = Files.readAllLines(file);

            // 只读前 N 行
            if (lines.size() > MAX_FRONTMATTER_LINES) {
                lines = lines.subList(0, MAX_FRONTMATTER_LINES);
            }

            // 解析 frontmatter
            MemoryFrontmatter frontmatter = fileWriter.parseFrontmatterFromLines(lines, file);
            if (frontmatter == null) {
                return null;
            }

            // 计算相对路径
            Path relativePath = memoryRoot.relativize(file);

            return new ScannedMemory(
                    file,
                    relativePath,
                    frontmatter,
                    frontmatter.getType()
            );
        } catch (IOException e) {
            log.error("Failed to read file: {}", file, e);
            return null;
        }
    }

    /**
     * 扫描结果内部类
     */
    public static class ScannedMemory {
        private final Path absolutePath;
        private final Path relativePath;
        private final MemoryFrontmatter frontmatter;
        private final MemoryType type;

        public ScannedMemory(Path absolutePath, Path relativePath,
                             MemoryFrontmatter frontmatter, MemoryType type) {
            this.absolutePath = absolutePath;
            this.relativePath = relativePath;
            this.frontmatter = frontmatter;
            this.type = type;
        }

        public Path getAbsolutePath() {
            return absolutePath;
        }

        public Path getRelativePath() {
            return relativePath;
        }

        public MemoryFrontmatter getFrontmatter() {
            return frontmatter;
        }

        public MemoryType getType() {
            return type;
        }

        public String getName() {
            return frontmatter.getName();
        }

        public String getDescription() {
            return frontmatter.getDescription();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", type.getDirectory(), getName(), getDescription());
        }
    }
}
