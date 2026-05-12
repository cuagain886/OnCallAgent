package org.example.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Token 预算管理器
 *
 * 负责 Token 计数和上下文预算分配。
 * 使用简单估算方法：中文约 1.5 token/字符，英文约 1 token/词。
 */
@Component
public class TokenBudgetManager {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetManager.class);

    @Value("${context.budget.enabled:true}")
    private boolean enabled;

    @Value("${context.budget.max-tokens:8000}")
    private int maxTokens;

    @Value("${context.budget.system-prompt-weight:0.15}")
    private double systemPromptWeight;

    @Value("${context.budget.rag-weight:0.25}")
    private double ragWeight;

    @Value("${context.budget.memory-weight:0.15}")
    private double memoryWeight;

    @Value("${context.budget.conversation-weight:0.30}")
    private double conversationWeight;

    @Value("${context.budget.user-input-weight:0.15}")
    private double userInputWeight;

    /**
     * 估算文本的 Token 数量
     *
     * 使用简单启发式方法：
     * - 中文字符：约 1.5 token/字符
     * - 英文单词：约 1 token/词
     * - 标点符号：约 0.5 token/个
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int englishWords = 0;
        int punctuation = 0;
        boolean inWord = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isChinese(c)) {
                chineseChars++;
                inWord = false;
            } else if (isLetter(c)) {
                if (!inWord) {
                    englishWords++;
                    inWord = true;
                }
            } else if (isPunctuation(c)) {
                punctuation++;
                inWord = false;
            } else {
                inWord = false;
            }
        }

        // 估算公式：中文 * 1.5 + 英文单词 * 1.0 + 标点 * 0.5
        return (int)(chineseChars * 1.5 + englishWords * 1.0 + punctuation * 0.5);
    }

    /**
     * 截断文本到指定 Token 数量
     */
    public String truncateToTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int currentTokens = estimateTokens(text);
        if (currentTokens <= maxTokens) {
            return text;
        }

        // 按比例截断
        double ratio = (double) maxTokens / currentTokens;
        int targetLength = (int)(text.length() * ratio * 0.9); // 预留 10% 缓冲

        if (targetLength >= text.length()) {
            return text;
        }

        // 截断并添加省略号
        return text.substring(0, targetLength) + "...(truncated)";
    }

    /**
     * 分配上下文预算
     *
     * @param sections 上下文片段列表
     * @return 预算分配结果
     */
    public ContextAllocation allocate(List<ContextSection> sections) {
        if (!enabled) {
            // 未启用时，返回无限预算
            Map<ContextSection, Integer> allocation = new LinkedHashMap<>();
            for (ContextSection section : sections) {
                allocation.put(section, estimateTokens(section.getContent()));
            }
            return new ContextAllocation(allocation, Integer.MAX_VALUE);
        }

        Map<ContextSection, Integer> allocation = new LinkedHashMap<>();

        // 1. 计算固定开销
        int fixedCost = 0;
        for (ContextSection section : sections) {
            if (section.isFixed()) {
                int tokens = estimateTokens(section.getContent());
                allocation.put(section, tokens);
                fixedCost += tokens;
            }
        }

        // 2. 计算剩余预算
        int remainingBudget = maxTokens - fixedCost;
        if (remainingBudget < 0) {
            log.warn("Fixed cost exceeds max tokens: fixed={}, max={}", fixedCost, maxTokens);
            remainingBudget = 0;
        }

        // 3. 按类型分配权重
        Map<ContextSection.SectionType, Double> typeWeights = Map.of(
                ContextSection.SectionType.SYSTEM_PROMPT, systemPromptWeight,
                ContextSection.SectionType.TOOL_DEFINITION, systemPromptWeight,
                ContextSection.SectionType.USER_PROFILE, memoryWeight,
                ContextSection.SectionType.MEMORY, memoryWeight,
                ContextSection.SectionType.RAG_RESULT, ragWeight,
                ContextSection.SectionType.CONVERSATION, conversationWeight,
                ContextSection.SectionType.USER_INPUT, userInputWeight
        );

        // 4. 按优先级分配动态片段
        List<ContextSection> dynamicSections = sections.stream()
                .filter(s -> !s.isFixed())
                .sorted(Comparator.comparingInt(ContextSection::getPriority).reversed())
                .toList();

        for (ContextSection section : dynamicSections) {
            double weight = typeWeights.getOrDefault(section.getType(), section.getWeight());
            int budget = (int)(remainingBudget * weight);
            int requested = estimateTokens(section.getContent());
            int allocated = Math.min(requested, budget);

            allocation.put(section, allocated);
        }

        ContextAllocation result = new ContextAllocation(allocation, maxTokens);
        log.debug("Context allocation: {}", result);
        return result;
    }

    /**
     * 根据预算截断上下文片段
     */
    public List<ContextSection> truncateSections(List<ContextSection> sections,
                                                  ContextAllocation allocation) {
        List<ContextSection> result = new ArrayList<>();

        for (ContextSection section : sections) {
            int budget = allocation.getBudget(section);
            int currentTokens = estimateTokens(section.getContent());

            if (currentTokens <= budget) {
                result.add(section);
            } else {
                String truncated = truncateToTokens(section.getContent(), budget);
                result.add(section.withContent(truncated));
            }
        }

        return result;
    }

    /**
     * 检查是否为中文字符
     */
    private boolean isChinese(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    /**
     * 检查是否为字母
     */
    private boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    /**
     * 检查是否为标点符号
     */
    private boolean isPunctuation(char c) {
        return Character.getType(c) == Character.CONNECTOR_PUNCTUATION
                || Character.getType(c) == Character.DASH_PUNCTUATION
                || Character.getType(c) == Character.END_PUNCTUATION
                || Character.getType(c) == Character.OTHER_PUNCTUATION
                || Character.getType(c) == Character.START_PUNCTUATION;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}
