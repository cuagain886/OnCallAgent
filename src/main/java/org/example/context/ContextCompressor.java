package org.example.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩器
 *
 * 负责对超限的上下文片段进行智能压缩，保留关键信息。
 * 支持多种压缩策略：
 * 1. 截断压缩：直接截断到目标长度
 * 2. 摘要压缩：保留首尾，中间部分生成摘要
 * 3. 重要性过滤：按重要性评分过滤内容
 */
@Component
public class ContextCompressor {

    @Value("${context.compression.enabled:true}")
    private boolean enabled;

    @Value("${context.compression.conversation-summary-threshold:10}")
    private int conversationSummaryThreshold;

    private final TokenBudgetManager budgetManager;

    public ContextCompressor(TokenBudgetManager budgetManager) {
        this.budgetManager = budgetManager;
    }

    /**
     * 压缩上下文片段
     *
     * @param sections   上下文片段列表
     * @param allocation 预算分配结果
     * @return 压缩后的片段列表
     */
    public List<ContextSection> compress(List<ContextSection> sections,
                                          ContextAllocation allocation) {
        if (!enabled) {
            return sections;
        }

        List<ContextSection> result = new ArrayList<>();

        for (ContextSection section : sections) {
            int budget = allocation.getBudget(section);
            int currentTokens = budgetManager.estimateTokens(section.getContent());

            if (currentTokens <= budget) {
                // 未超限，直接保留
                result.add(section);
            } else if (section.isFixed()) {
                // 固定片段，截断
                result.add(compressByTruncation(section, budget));
            } else {
                // 动态片段，智能压缩
                result.add(compressSection(section, budget));
            }
        }

        return result;
    }

    /**
     * 根据类型选择压缩策略
     */
    private ContextSection compressSection(ContextSection section, int targetTokens) {
        return switch (section.getType()) {
            case CONVERSATION -> compressConversation(section, targetTokens);
            case RAG_RESULT -> compressRagResult(section, targetTokens);
            case MEMORY -> compressMemory(section, targetTokens);
            default -> compressByTruncation(section, targetTokens);
        };
    }

    /**
     * 压缩对话历史
     *
     * 策略：保留最近的对话，对早期对话生成摘要
     */
    private ContextSection compressConversation(ContextSection section, int targetTokens) {
        String content = section.getContent();
        String[] lines = content.split("\n");

        if (lines.length <= conversationSummaryThreshold * 2) {
            // 对话轮数不多，直接截断
            return compressByTruncation(section, targetTokens);
        }

        // 保留最近的对话
        int keepLines = conversationSummaryThreshold * 2;
        StringBuilder recentConversation = new StringBuilder();
        for (int i = Math.max(0, lines.length - keepLines); i < lines.length; i++) {
            recentConversation.append(lines[i]).append("\n");
        }

        // 对早期对话生成摘要
        StringBuilder earlyConversation = new StringBuilder();
        for (int i = 0; i < Math.max(0, lines.length - keepLines); i++) {
            earlyConversation.append(lines[i]).append("\n");
        }

        String summary = summarizeConversation(earlyConversation.toString());

        // 组装压缩后的内容
        StringBuilder compressed = new StringBuilder();
        if (!summary.isEmpty()) {
            compressed.append("【早期对话摘要】\n").append(summary).append("\n\n");
        }
        compressed.append("【最近对话】\n").append(recentConversation);

        return section.withContent(compressed.toString().trim());
    }

    /**
     * 压缩 RAG 结果
     *
     * 策略：按段落分割，保留高相关性段落
     */
    private ContextSection compressRagResult(ContextSection section, int targetTokens) {
        String content = section.getContent();
        String[] paragraphs = content.split("\n\n");

        if (paragraphs.length <= 2) {
            // 段落不多，直接截断
            return compressByTruncation(section, targetTokens);
        }

        // 保留首段和尾段（通常包含最重要的信息）
        StringBuilder compressed = new StringBuilder();
        compressed.append(paragraphs[0]).append("\n\n");

        // 计算剩余预算
        int usedTokens = budgetManager.estimateTokens(paragraphs[0]);
        int remainingTokens = targetTokens - usedTokens;

        // 从中间段落中选择
        for (int i = 1; i < paragraphs.length - 1 && remainingTokens > 0; i++) {
            int paragraphTokens = budgetManager.estimateTokens(paragraphs[i]);
            if (paragraphTokens <= remainingTokens) {
                compressed.append(paragraphs[i]).append("\n\n");
                remainingTokens -= paragraphTokens;
            }
        }

        // 添加尾段
        if (paragraphs.length > 1) {
            int lastParagraphTokens = budgetManager.estimateTokens(paragraphs[paragraphs.length - 1]);
            if (lastParagraphTokens <= remainingTokens) {
                compressed.append(paragraphs[paragraphs.length - 1]);
            }
        }

        return section.withContent(compressed.toString().trim());
    }

    /**
     * 压缩记忆上下文
     *
     * 策略：按条目分割，保留高重要性条目
     */
    private ContextSection compressMemory(ContextSection section, int targetTokens) {
        String content = section.getContent();
        String[] items = content.split("\n- ");

        if (items.length <= 3) {
            // 条目不多，直接截断
            return compressByTruncation(section, targetTokens);
        }

        // 保留前几个条目（通常更重要）
        StringBuilder compressed = new StringBuilder();
        int usedTokens = 0;

        for (String item : items) {
            int itemTokens = budgetManager.estimateTokens(item);
            if (usedTokens + itemTokens <= targetTokens) {
                if (compressed.length() > 0) {
                    compressed.append("\n- ");
                }
                compressed.append(item);
                usedTokens += itemTokens;
            } else {
                break;
            }
        }

        return section.withContent(compressed.toString().trim());
    }

    /**
     * 截断压缩
     */
    private ContextSection compressByTruncation(ContextSection section, int targetTokens) {
        String truncated = budgetManager.truncateToTokens(section.getContent(), targetTokens);
        return section.withContent(truncated);
    }

    /**
     * 生成对话摘要
     */
    private String summarizeConversation(String conversation) {
        if (conversation == null || conversation.isEmpty()) {
            return "";
        }

        // 简单摘要：提取关键信息
        String[] lines = conversation.split("\n");
        StringBuilder summary = new StringBuilder();

        // 提取用户问题
        for (String line : lines) {
            if (line.startsWith("user:") || line.startsWith("用户:")) {
                String question = line.substring(line.indexOf(":") + 1).trim();
                if (!question.isEmpty()) {
                    summary.append("用户询问了关于").append(truncate(question, 20)).append("的问题。");
                    break;
                }
            }
        }

        // 提取助手回答要点
        for (String line : lines) {
            if (line.startsWith("assistant:") || line.startsWith("助手:")) {
                String answer = line.substring(line.indexOf(":") + 1).trim();
                if (!answer.isEmpty()) {
                    summary.append("助手提供了").append(truncate(answer, 30)).append("的回答。");
                    break;
                }
            }
        }

        return summary.toString();
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    public boolean isEnabled() {
        return enabled;
    }
}
