package org.example.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextPipeline 单元测试
 */
class ContextPipelineTest {

    private ContextPipeline pipeline;
    private TokenBudgetManager budgetManager;

    @BeforeEach
    void setUp() {
        budgetManager = new TokenBudgetManager();

        // 使用反射设置私有字段
        try {
            var enabledField = TokenBudgetManager.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(budgetManager, true);

            var maxTokensField = TokenBudgetManager.class.getDeclaredField("maxTokens");
            maxTokensField.setAccessible(true);
            maxTokensField.set(budgetManager, 8000);

            var systemPromptWeightField = TokenBudgetManager.class.getDeclaredField("systemPromptWeight");
            systemPromptWeightField.setAccessible(true);
            systemPromptWeightField.set(budgetManager, 0.15);

            var ragWeightField = TokenBudgetManager.class.getDeclaredField("ragWeight");
            ragWeightField.setAccessible(true);
            ragWeightField.set(budgetManager, 0.25);

            var memoryWeightField = TokenBudgetManager.class.getDeclaredField("memoryWeight");
            memoryWeightField.setAccessible(true);
            memoryWeightField.set(budgetManager, 0.15);

            var conversationWeightField = TokenBudgetManager.class.getDeclaredField("conversationWeight");
            conversationWeightField.setAccessible(true);
            conversationWeightField.set(budgetManager, 0.30);

            var userInputWeightField = TokenBudgetManager.class.getDeclaredField("userInputWeight");
            userInputWeightField.setAccessible(true);
            userInputWeightField.set(budgetManager, 0.15);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        pipeline = new ContextPipeline();

        // 使用反射设置私有字段
        try {
            var budgetManagerField = ContextPipeline.class.getDeclaredField("budgetManager");
            budgetManagerField.setAccessible(true);
            budgetManagerField.set(pipeline, budgetManager);

            var enabledField = ContextPipeline.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(pipeline, true);

            var maxTokensField = ContextPipeline.class.getDeclaredField("maxTokens");
            maxTokensField.setAccessible(true);
            maxTokensField.set(pipeline, 8000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testAssembleWithAllSections() {
        // Given
        String systemPrompt = "你是一个智能助手";
        String ragContext = "这是 RAG 检索结果";
        String memoryContext = "这是记忆上下文";
        String userQuery = "用户的问题";

        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(userQuery)
                .messageCount(5)
                .recentMessages(List.of(
                        Map.of("role", "user", "content", "历史问题"),
                        Map.of("role", "assistant", "content", "历史回答")
                ))
                .build();

        // When
        AssembledContext assembled = pipeline.assemble(systemPrompt, ragContext, memoryContext, userQuery, context);

        // Then
        assertNotNull(assembled);
        assertTrue(assembled.getSectionCount() > 0);
        assertTrue(assembled.getTotalTokens() > 0);
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.SYSTEM_PROMPT));
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.RAG_RESULT));
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.MEMORY));
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.CONVERSATION));
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.USER_INPUT));
    }

    @Test
    void testAssembleWithPartialSections() {
        // Given
        String systemPrompt = "你是一个智能助手";
        String userQuery = "用户的问题";

        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(userQuery)
                .messageCount(0)
                .build();

        // When
        AssembledContext assembled = pipeline.assemble(systemPrompt, null, null, userQuery, context);

        // Then
        assertNotNull(assembled);
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.SYSTEM_PROMPT));
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.USER_INPUT));
        assertFalse(assembled.hasSectionType(ContextSection.SectionType.RAG_RESULT));
        assertFalse(assembled.hasSectionType(ContextSection.SectionType.MEMORY));
    }

    @Test
    void testAssembleRespectsTokenBudget() {
        // Given - 创建超长内容
        String longRagContext = "这是一段很长的 RAG 检索结果。".repeat(1000);
        String userQuery = "用户的问题";

        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(userQuery)
                .messageCount(0)
                .build();

        // When
        AssembledContext assembled = pipeline.assemble(null, longRagContext, null, userQuery, context);

        // Then
        assertNotNull(assembled);
        assertTrue(assembled.getTotalTokens() <= 8000);
    }

    @Test
    void testAssembleSimple() {
        // Given
        String systemPrompt = "你是一个智能助手";
        String userQuery = "用户的问题";

        // When
        AssembledContext assembled = pipeline.assembleSimple(systemPrompt, userQuery);

        // Then
        assertNotNull(assembled);
        assertEquals(2, assembled.getSectionCount());
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.SYSTEM_PROMPT));
        assertTrue(assembled.hasSectionType(ContextSection.SectionType.USER_INPUT));
    }

    @Test
    void testAssembleWithEmptyInputs() {
        // Given
        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery("")
                .messageCount(0)
                .build();

        // When
        AssembledContext assembled = pipeline.assemble(null, null, null, "", context);

        // Then
        assertNotNull(assembled);
        assertEquals(0, assembled.getSectionCount());
    }

    @Test
    void testGetSystemPromptContent() {
        // Given
        String systemPrompt = "你是一个智能助手";
        String ragContext = "RAG 内容";
        String memoryContext = "记忆内容";
        String userQuery = "用户问题";

        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(userQuery)
                .messageCount(0)
                .build();

        // When
        AssembledContext assembled = pipeline.assemble(systemPrompt, ragContext, memoryContext, userQuery, context);
        String systemContent = assembled.getSystemPromptContent();

        // Then
        assertNotNull(systemContent);
        assertTrue(systemContent.contains(systemPrompt));
        assertTrue(systemContent.contains(ragContext));
        assertTrue(systemContent.contains(memoryContext));
    }

    @Test
    void testGetUserInputContent() {
        // Given
        String userQuery = "用户的问题";

        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(userQuery)
                .messageCount(0)
                .build();

        // When
        AssembledContext assembled = pipeline.assemble(null, null, null, userQuery, context);
        String userInput = assembled.getUserInputContent();

        // Then
        assertNotNull(userInput);
        assertEquals(userQuery, userInput);
    }

    @Test
    void testGetSectionByType() {
        // Given
        String ragContext = "RAG 内容";

        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery("问题")
                .messageCount(0)
                .build();

        // When
        AssembledContext assembled = pipeline.assemble(null, ragContext, null, "问题", context);
        ContextSection ragSection = assembled.getSectionByType(ContextSection.SectionType.RAG_RESULT);

        // Then
        assertNotNull(ragSection);
        assertEquals(ragContext, ragSection.getContent());
    }

    @Test
    void testDisabledPipeline() throws Exception {
        // Given
        var enabledField = ContextPipeline.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(pipeline, false);

        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery("问题")
                .messageCount(0)
                .build();

        // When
        AssembledContext assembled = pipeline.assemble("提示词", "RAG", "记忆", "问题", context);

        // Then
        assertNotNull(assembled);
        // 即使禁用，仍然会组装上下文
    }
}
