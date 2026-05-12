package org.example.memory.markdown;

/**
 * 记忆类型枚举
 */
public enum MemoryType {

    /**
     * 用户画像：存储偏好语言、工作风格、经验水平
     */
    USER("user", "用户画像"),

    /**
     * 项目上下文：项目技术栈、架构约定、文件结构
     */
    PROJECT("project", "项目上下文"),

    /**
     * 行为反馈：双向记录 - 错误纠正 + 成功确认
     */
    FEEDBACK("feedback", "行为反馈"),

    /**
     * 外部指针：指向外部文档/URL 的指针索引
     */
    REFERENCE("reference", "外部指针");

    private final String directory;
    private final String description;

    MemoryType(String directory, String description) {
        this.directory = directory;
        this.description = description;
    }

    public String getDirectory() {
        return directory;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 从字符串解析 MemoryType
     */
    public static MemoryType fromString(String type) {
        for (MemoryType mt : values()) {
            if (mt.directory.equalsIgnoreCase(type) || mt.name().equalsIgnoreCase(type)) {
                return mt;
            }
        }
        throw new IllegalArgumentException("Unknown memory type: " + type);
    }
}
