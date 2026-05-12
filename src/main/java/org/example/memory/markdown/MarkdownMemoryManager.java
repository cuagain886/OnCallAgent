package org.example.memory.markdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Markdown 记忆管理器
 *
 * 核心管理器，提供 Markdown 记忆文件的 CRUD 操作和索引管理。
 * 存储位置：~/.agent-memory/
 */
@Component
public class MarkdownMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(MarkdownMemoryManager.class);

    private final MemoryFileWriter fileWriter;
    private final MemoryIndexManager indexManager;

    @Value("${memory.markdown.enabled:true}")
    private boolean enabled;

    @Value("${memory.markdown.root:#{systemProperties['user.home']}/.agent-memory}")
    private String memoryRootPath;

    private Path memoryRoot;

    public MarkdownMemoryManager(MemoryFileWriter fileWriter, MemoryIndexManager indexManager) {
        this.fileWriter = fileWriter;
        this.indexManager = indexManager;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Markdown memory is disabled");
            return;
        }

        memoryRoot = Paths.get(memoryRootPath);
        ensureDirectoryStructure();
        log.info("Markdown memory initialized at: {}", memoryRoot);
    }

    /**
     * 确保目录结构存在
     */
    private void ensureDirectoryStructure() {
        try {
            java.nio.file.Files.createDirectories(memoryRoot);
            for (MemoryType type : MemoryType.values()) {
                java.nio.file.Files.createDirectories(memoryRoot.resolve(type.getDirectory()));
            }
            log.debug("Ensured memory directory structure");
        } catch (java.io.IOException e) {
            log.error("Failed to create memory directory structure", e);
        }
    }

    /**
     * 保存记忆
     *
     * @param type        记忆类型
     * @param fileName    文件名（不含扩展名）
     * @param name        记忆名称
     * @param description 记忆描述（不超过150字符）
     * @param content     记忆内容
     */
    public void saveMemory(MemoryType type, String fileName, String name,
                           String description, String content) {
        if (!enabled) {
            return;
        }

        Path filePath = getFilePath(type, fileName);
        MemoryFrontmatter frontmatter = new MemoryFrontmatter(name, description, type);
        fileWriter.create(filePath, frontmatter, content);

        // 更新索引
        indexManager.rebuildIndex(memoryRoot);
        log.info("Saved memory: {}/{}", type.getDirectory(), fileName);
    }

    /**
     * 更新记忆
     */
    public void updateMemory(MemoryType type, String fileName, String content) {
        if (!enabled) {
            return;
        }

        Path filePath = getFilePath(type, fileName);
        if (!fileWriter.exists(filePath)) {
            log.warn("Memory file not found: {}", filePath);
            return;
        }

        fileWriter.update(filePath, content);
        indexManager.rebuildIndex(memoryRoot);
        log.info("Updated memory: {}/{}", type.getDirectory(), fileName);
    }

    /**
     * 追加记忆内容
     */
    public void appendMemory(MemoryType type, String fileName, String name,
                             String description, String section) {
        if (!enabled) {
            return;
        }

        Path filePath = getFilePath(type, fileName);
        fileWriter.appendOrCreate(filePath, name, description, type, section);
        indexManager.rebuildIndex(memoryRoot);
        log.info("Appended to memory: {}/{}", type.getDirectory(), fileName);
    }

    /**
     * 读取记忆内容
     */
    public String readMemory(MemoryType type, String fileName) {
        if (!enabled) {
            return null;
        }

        Path filePath = getFilePath(type, fileName);
        return fileWriter.readBody(filePath);
    }

    /**
     * 读取记忆的完整内容（包含 frontmatter）
     */
    public String readMemoryFull(MemoryType type, String fileName) {
        if (!enabled) {
            return null;
        }

        Path filePath = getFilePath(type, fileName);
        return fileWriter.readFullContent(filePath);
    }

    /**
     * 删除记忆
     */
    public boolean deleteMemory(MemoryType type, String fileName) {
        if (!enabled) {
            return false;
        }

        Path filePath = getFilePath(type, fileName);
        boolean deleted = fileWriter.delete(filePath);
        if (deleted) {
            indexManager.rebuildIndex(memoryRoot);
            log.info("Deleted memory: {}/{}", type.getDirectory(), fileName);
        }
        return deleted;
    }

    /**
     * 检查记忆是否存在
     */
    public boolean memoryExists(MemoryType type, String fileName) {
        if (!enabled) {
            return false;
        }

        Path filePath = getFilePath(type, fileName);
        return fileWriter.exists(filePath);
    }

    /**
     * 获取记忆文件路径
     */
    public Path getFilePath(MemoryType type, String fileName) {
        if (!fileName.endsWith(".md")) {
            fileName = fileName + ".md";
        }
        return memoryRoot.resolve(type.getDirectory()).resolve(fileName);
    }

    /**
     * 获取记忆根目录
     */
    public Path getMemoryRoot() {
        return memoryRoot;
    }

    /**
     * 获取 MEMORY.md 索引内容
     */
    public String getIndexContent() {
        if (!enabled) {
            return null;
        }

        return indexManager.readIndex(memoryRoot);
    }

    /**
     * 重建索引
     */
    public void rebuildIndex() {
        if (!enabled) {
            return;
        }

        indexManager.rebuildIndex(memoryRoot);
    }

    /**
     * 验证索引是否符合限制
     */
    public boolean validateIndex() {
        if (!enabled) {
            return true;
        }

        return indexManager.validateIndex(memoryRoot);
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
