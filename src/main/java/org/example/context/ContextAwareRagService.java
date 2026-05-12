package org.example.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 上下文感知的 RAG 服务
 *
 * 增强 RAG 检索的上下文感知能力，包括：
 * 1. 查询改写：将指代性查询转换为完整查询
 * 2. 实体提取：从对话中提取关键实体
 * 3. 多路召回：结合向量检索和实体检索
 */
@Component
public class ContextAwareRagService {

    private static final Logger log = LoggerFactory.getLogger(ContextAwareRagService.class);

    @Autowired(required = false)
    private ChatModel chatModel;

    @Value("${context.rag.enabled:true}")
    private boolean enabled;

    @Value("${context.rag.rewrite.enabled:true}")
    private boolean rewriteEnabled;

    @Value("${context.rag.entity-extraction.enabled:true}")
    private boolean entityExtractionEnabled;

    /**
     * 上下文感知的查询改写
     *
     * 将指代性查询转换为完整查询
     * 例：用户问"它的价格呢？" → 改写为"iPhone 15 的价格是多少？"
     */
    public String rewriteQuery(String query, ConversationContext context) {
        if (!enabled || !rewriteEnabled || chatModel == null) {
            return query;
        }

        try {
            String recentMessages = context.formatRecentMessages();
            if (recentMessages.isEmpty()) {
                return query;
            }

            String prompt = """
                根据对话历史，将用户的查询改写为完整的、独立的查询。

                对话历史：
                %s

                用户查询：%s

                请返回改写后的查询，不要有其他内容。如果查询已经是完整的，直接返回原查询。
                """.formatted(recentMessages, query);

            String rewritten = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText()
                    .trim();

            // 如果改写结果太长或为空，使用原查询
            if (rewritten.isEmpty() || rewritten.length() > query.length() * 3) {
                return query;
            }

            log.debug("查询改写: {} -> {}", query, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("查询改写失败，使用原查询", e);
            return query;
        }
    }

    /**
     * 从对话中提取关键实体
     *
     * 提取服务名、指标名、技术术语等
     */
    public List<String> extractEntities(ConversationContext context) {
        if (!enabled || !entityExtractionEnabled || chatModel == null) {
            return Collections.emptyList();
        }

        try {
            String recentMessages = context.formatRecentMessages();
            if (recentMessages.isEmpty()) {
                return Collections.emptyList();
            }

            String prompt = """
                从以下对话中提取关键实体（服务名、指标名、技术术语等）。

                对话内容：
                %s

                请返回实体列表，每行一个实体，不要有其他内容。
                """.formatted(recentMessages);

            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            List<String> entities = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("-") && !s.startsWith("*"))
                    .limit(10) // 限制实体数量
                    .toList();

            log.debug("提取到 {} 个实体: {}", entities.size(), entities);
            return entities;
        } catch (Exception e) {
            log.warn("实体提取失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建增强的 RAG 上下文
     *
     * 将 RAG 结果和实体信息组合
     */
    public String buildEnhancedContext(String ragContext, List<String> entities) {
        if (entities == null || entities.isEmpty()) {
            return ragContext;
        }

        StringBuilder enhanced = new StringBuilder();

        // 添加 RAG 结果
        if (ragContext != null && !ragContext.isEmpty()) {
            enhanced.append(ragContext);
        }

        // 添加实体信息
        enhanced.append("\n\n【相关实体】\n");
        for (String entity : entities) {
            enhanced.append("- ").append(entity).append("\n");
        }

        return enhanced.toString().trim();
    }

    /**
     * 完整的上下文感知 RAG 流程
     */
    public ContextAwareRagResult process(String query, String ragContext,
                                          ConversationContext context) {
        // 1. 查询改写
        String rewrittenQuery = rewriteQuery(query, context);

        // 2. 实体提取
        List<String> entities = extractEntities(context);

        // 3. 构建增强上下文
        String enhancedContext = buildEnhancedContext(ragContext, entities);

        return new ContextAwareRagResult(rewrittenQuery, entities, enhancedContext);
    }

    /**
     * 上下文感知 RAG 结果
     */
    public static class ContextAwareRagResult {
        private final String rewrittenQuery;
        private final List<String> entities;
        private final String enhancedContext;

        public ContextAwareRagResult(String rewrittenQuery, List<String> entities,
                                      String enhancedContext) {
            this.rewrittenQuery = rewrittenQuery;
            this.entities = entities;
            this.enhancedContext = enhancedContext;
        }

        public String getRewrittenQuery() {
            return rewrittenQuery;
        }

        public List<String> getEntities() {
            return entities;
        }

        public String getEnhancedContext() {
            return enhancedContext;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
