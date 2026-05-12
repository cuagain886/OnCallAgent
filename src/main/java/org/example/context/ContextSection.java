package org.example.context;

/**
 * 上下文片段
 *
 * 表示上下文中的一个组成部分，如系统提示词、RAG 结果、记忆上下文等。
 */
public class ContextSection {

    /**
     * 片段类型
     */
    public enum SectionType {
        SYSTEM_PROMPT,      // 系统提示词
        TOOL_DEFINITION,    // 工具定义
        USER_PROFILE,       // 用户画像
        MEMORY,             // 记忆上下文
        RAG_RESULT,         // RAG 检索结果
        CONVERSATION,       // 对话历史
        USER_INPUT          // 用户输入
    }

    private final String id;
    private final String content;
    private final SectionType type;
    private final int priority;      // 优先级 1-10，数字越大越重要
    private final double weight;     // 权重 0.0-1.0，占总预算的比例
    private final boolean fixed;     // 是否为固定开销（不可压缩）

    private ContextSection(Builder builder) {
        this.id = builder.id;
        this.content = builder.content;
        this.type = builder.type;
        this.priority = builder.priority;
        this.weight = builder.weight;
        this.fixed = builder.fixed;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public SectionType getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    public double getWeight() {
        return weight;
    }

    public boolean isFixed() {
        return fixed;
    }

    /**
     * 创建截断后的副本
     */
    public ContextSection withContent(String newContent) {
        return builder()
                .id(id)
                .content(newContent)
                .type(type)
                .priority(priority)
                .weight(weight)
                .fixed(fixed)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String content;
        private SectionType type;
        private int priority = 5;
        private double weight = 0.1;
        private boolean fixed = false;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder type(SectionType type) {
            this.type = type;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        public Builder fixed(boolean fixed) {
            this.fixed = fixed;
            return this;
        }

        public ContextSection build() {
            return new ContextSection(this);
        }
    }
}
