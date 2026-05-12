package org.example.context;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组装后的上下文
 *
 * 包含所有上下文片段和元信息，用于传递给 LLM。
 */
public class AssembledContext {

    private final List<ContextSection> sections;
    private final int totalTokens;
    private final ContextAllocation allocation;
    private final Map<String, Object> metadata;

    private AssembledContext(Builder builder) {
        this.sections = builder.sections;
        this.totalTokens = builder.totalTokens;
        this.allocation = builder.allocation;
        this.metadata = builder.metadata;
    }

    public List<ContextSection> getSections() {
        return sections;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public ContextAllocation getAllocation() {
        return allocation;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 获取指定类型的片段
     */
    public List<ContextSection> getSectionsByType(ContextSection.SectionType type) {
        return sections.stream()
                .filter(s -> s.getType() == type)
                .toList();
    }

    /**
     * 获取指定类型的第一个片段
     */
    public ContextSection getSectionByType(ContextSection.SectionType type) {
        return sections.stream()
                .filter(s -> s.getType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取系统提示词内容
     */
    public String getSystemPromptContent() {
        return sections.stream()
                .filter(s -> s.getType() == ContextSection.SectionType.SYSTEM_PROMPT
                        || s.getType() == ContextSection.SectionType.USER_PROFILE
                        || s.getType() == ContextSection.SectionType.MEMORY
                        || s.getType() == ContextSection.SectionType.RAG_RESULT)
                .map(ContextSection::getContent)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 获取用户输入内容
     */
    public String getUserInputContent() {
        return sections.stream()
                .filter(s -> s.getType() == ContextSection.SectionType.USER_INPUT)
                .map(ContextSection::getContent)
                .findFirst()
                .orElse("");
    }

    /**
     * 检查是否包含指定类型
     */
    public boolean hasSectionType(ContextSection.SectionType type) {
        return sections.stream().anyMatch(s -> s.getType() == type);
    }

    /**
     * 获取片段数量
     */
    public int getSectionCount() {
        return sections.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ContextSection> sections;
        private int totalTokens;
        private ContextAllocation allocation;
        private Map<String, Object> metadata;

        public Builder sections(List<ContextSection> sections) {
            this.sections = sections;
            return this;
        }

        public Builder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder allocation(ContextAllocation allocation) {
            this.allocation = allocation;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AssembledContext build() {
            return new AssembledContext(this);
        }
    }
}
