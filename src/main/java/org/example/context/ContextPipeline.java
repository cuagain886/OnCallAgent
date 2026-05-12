package org.example.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文组装流水线
 *
 * 负责协调各个上下文片段的组装，包括：
 * 1. 用户画像注入
 * 2. 记忆召回
 * 3. 知识检索（RAG）
 * 4. Token 预算分配
 * 5. 上下文压缩
 */
@Component
public class ContextPipeline {

    private static final Logger log = LoggerFactory.getLogger(ContextPipeline.class);

    @Autowired
    private TokenBudgetManager budgetManager;

    @Autowired(required = false)
    private ContextCompressor compressor;

    @Autowired(required = false)
    private UserProfileInjector userProfileInjector;

    @Value("${context.budget.enabled:true}")
    private boolean enabled;

    @Value("${context.budget.max-tokens:8000}")
    private int maxTokens;

    /**
     * 组装上下文
     *
     * @param systemPrompt 系统提示词
     * @param ragContext   RAG 检索结果
     * @param memoryContext 记忆上下文
     * @param userQuery    用户查询
     * @param context      对话上下文
     * @return 组装后的上下文
     */
    public AssembledContext assemble(String systemPrompt, String ragContext,
                                      String memoryContext, String userQuery,
                                      ConversationContext context) {
        log.debug("开始组装上下文: session={}, user={}", context.getSessionId(), context.getUserId());

        // Step 1: 收集所有上下文片段
        List<ContextSection> sections = new ArrayList<>();

        // 1.0 用户画像注入
        if (userProfileInjector != null && userProfileInjector.isEnabled()) {
            ContextSection userProfile = userProfileInjector.inject(context.getUserId());
            if (userProfile != null && !userProfile.getContent().isEmpty()) {
                sections.add(userProfile);
                log.debug("注入用户画像: userId={}", context.getUserId());
            }
        }

        // 1.1 系统提示词（固定）
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sections.add(ContextSection.builder()
                    .id("system_prompt")
                    .content(systemPrompt)
                    .type(ContextSection.SectionType.SYSTEM_PROMPT)
                    .priority(10)
                    .fixed(true)
                    .build());
        }

        // 1.2 RAG 检索结果
        if (ragContext != null && !ragContext.isEmpty()) {
            sections.add(ContextSection.builder()
                    .id("rag_result")
                    .content(ragContext)
                    .type(ContextSection.SectionType.RAG_RESULT)
                    .priority(8)
                    .build());
        }

        // 1.3 记忆上下文
        if (memoryContext != null && !memoryContext.isEmpty()) {
            sections.add(ContextSection.builder()
                    .id("memory")
                    .content(memoryContext)
                    .type(ContextSection.SectionType.MEMORY)
                    .priority(7)
                    .build());
        }

        // 1.4 对话历史
        String conversationHistory = context.formatRecentMessages();
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            sections.add(ContextSection.builder()
                    .id("conversation")
                    .content(conversationHistory)
                    .type(ContextSection.SectionType.CONVERSATION)
                    .priority(6)
                    .build());
        }

        // 1.5 用户输入（固定）
        if (userQuery != null && !userQuery.isEmpty()) {
            sections.add(ContextSection.builder()
                    .id("user_input")
                    .content(userQuery)
                    .type(ContextSection.SectionType.USER_INPUT)
                    .priority(10)
                    .fixed(true)
                    .build());
        }

        // Step 2: Token 预算分配
        ContextAllocation allocation = budgetManager.allocate(sections);

        // Step 3: 根据预算压缩片段（使用智能压缩器或简单截断）
        List<ContextSection> truncatedSections;
        if (compressor != null && compressor.isEnabled()) {
            truncatedSections = compressor.compress(sections, allocation);
        } else {
            truncatedSections = budgetManager.truncateSections(sections, allocation);
        }

        // Step 4: 构建组装后的上下文
        AssembledContext assembled = AssembledContext.builder()
                .sections(truncatedSections)
                .totalTokens(allocation.getUsedTokens())
                .allocation(allocation)
                .metadata(buildMetadata(context))
                .build();

        log.debug("上下文组装完成: sections={}, tokens={}, allocation={}",
                assembled.getSectionCount(), assembled.getTotalTokens(), allocation);

        return assembled;
    }

    /**
     * 组装简化版上下文（只有系统提示词和用户输入）
     */
    public AssembledContext assembleSimple(String systemPrompt, String userQuery) {
        List<ContextSection> sections = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sections.add(ContextSection.builder()
                    .id("system_prompt")
                    .content(systemPrompt)
                    .type(ContextSection.SectionType.SYSTEM_PROMPT)
                    .priority(10)
                    .fixed(true)
                    .build());
        }

        if (userQuery != null && !userQuery.isEmpty()) {
            sections.add(ContextSection.builder()
                    .id("user_input")
                    .content(userQuery)
                    .type(ContextSection.SectionType.USER_INPUT)
                    .priority(10)
                    .fixed(true)
                    .build());
        }

        ContextAllocation allocation = budgetManager.allocate(sections);

        return AssembledContext.builder()
                .sections(sections)
                .totalTokens(allocation.getUsedTokens())
                .allocation(allocation)
                .build();
    }

    /**
     * 构建元数据
     */
    private Map<String, Object> buildMetadata(ConversationContext context) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", context.getSessionId());
        metadata.put("userId", context.getUserId());
        metadata.put("messageCount", context.getMessageCount());
        metadata.put("startTime", context.getStartTime());
        return metadata;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}
