package org.example.memory.markdown;

import java.time.LocalDate;
import java.util.List;

/**
 * Markdown 记忆文件的 Frontmatter 数据模型
 */
public class MemoryFrontmatter {

    private String name;
    private String description;
    private MemoryType type;
    private LocalDate created;
    private LocalDate updated;
    private List<String> tags;

    public MemoryFrontmatter() {
    }

    public MemoryFrontmatter(String name, String description, MemoryType type) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.created = LocalDate.now();
        this.updated = LocalDate.now();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type;
    }

    public LocalDate getCreated() {
        return created;
    }

    public void setCreated(LocalDate created) {
        this.created = created;
    }

    public LocalDate getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDate updated) {
        this.updated = updated;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * 生成 YAML 格式的 frontmatter 字符串
     */
    public String toYaml() {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append("\n");
        sb.append("description: ").append(description).append("\n");
        sb.append("type: ").append(type.getDirectory()).append("\n");
        sb.append("created: ").append(created).append("\n");
        sb.append("updated: ").append(updated).append("\n");
        if (tags != null && !tags.isEmpty()) {
            sb.append("tags: [").append(String.join(", ", tags)).append("]\n");
        }
        sb.append("---\n");
        return sb.toString();
    }

    /**
     * 从文件路径提取名称
     */
    public static String extractNameFromPath(java.nio.file.Path path) {
        String fileName = path.getFileName().toString();
        // 移除 .md 扩展名
        if (fileName.endsWith(".md")) {
            fileName = fileName.substring(0, fileName.length() - 3);
        }
        // 将下划线转换为空格，首字母大写
        return fileName.replace("_", " ");
    }
}
