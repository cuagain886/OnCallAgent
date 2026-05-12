package org.example.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextCompressor 单元测试
 */
class ContextCompressorTest {

    private ContextCompressor compressor;
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

        compressor = new ContextCompressor(budgetManager);

        // 使用反射设置私有字段
        try {
            var enabledField = ContextCompressor.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(compressor, true);

            var thresholdField = ContextCompressor.class.getDeclaredField("conversationSummaryThreshold");
            thresholdField.setAccessible(true);
            thresholdField.set(compressor, 10);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testCompressNoCompressionNeeded() {
        // Given
        List<ContextSection> sections = new ArrayList<>();
        sections.add(ContextSection.builder()
                .id("test")
                .content("短内容")
                .type(ContextSection.SectionType.RAG_RESULT)
                .priority(8)
                .build());

        ContextAllocation allocation = budgetManager.allocate(sections);

        // When
        List<ContextSection> compressed = compressor.compress(sections, allocation);

        // Then
        assertEquals(1, compressed.size());
        assertEquals("短内容", compressed.get(0).getContent());
    }

    @Test
    void testCompressFixedSection() {
        // Given
        String longContent = "这是一段很长的内容。".repeat(100);
        List<ContextSection> sections = new ArrayList<>();
        sections.add(ContextSection.builder()
                .id("fixed")
                .content(longContent)
                .type(ContextSection.SectionType.SYSTEM_PROMPT)
                .priority(10)
                .fixed(true)
                .build());

        ContextAllocation allocation = budgetManager.allocate(sections);

        // When
        List<ContextSection> compressed = compressor.compress(sections, allocation);

        // Then
        assertEquals(1, compressed.size());
        assertTrue(budgetManager.estimateTokens(compressed.get(0).getContent())
                <= allocation.getBudget(sections.get(0)));
    }

    @Test
    void testCompressRagResult() {
        // Given - 创建多段落的 RAG 结果
        StringBuilder ragContent = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            ragContent.append("段落 ").append(i).append(": ").append("这是内容。".repeat(20)).append("\n\n");
        }

        List<ContextSection> sections = new ArrayList<>();
        sections.add(ContextSection.builder()
                .id("rag")
                .content(ragContent.toString().trim())
                .type(ContextSection.SectionType.RAG_RESULT)
                .priority(8)
                .build());

        ContextAllocation allocation = budgetManager.allocate(sections);

        // When
        List<ContextSection> compressed = compressor.compress(sections, allocation);

        // Then
        assertEquals(1, compressed.size());
        assertTrue(budgetManager.estimateTokens(compressed.get(0).getContent())
                <= allocation.getBudget(sections.get(0)));
    }

    @Test
    void testCompressConversation() {
        // Given - 创建多轮对话
        StringBuilder conversation = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            conversation.append("user: 用户问题 ").append(i).append("\n");
            conversation.append("assistant: 助手回答 ").append(i).append("\n");
        }

        List<ContextSection> sections = new ArrayList<>();
        sections.add(ContextSection.builder()
                .id("conversation")
                .content(conversation.toString().trim())
                .type(ContextSection.SectionType.CONVERSATION)
                .priority(6)
                .build());

        ContextAllocation allocation = budgetManager.allocate(sections);

        // When
        List<ContextSection> compressed = compressor.compress(sections, allocation);

        // Then
        assertEquals(1, compressed.size());
        assertTrue(budgetManager.estimateTokens(compressed.get(0).getContent())
                <= allocation.getBudget(sections.get(0)));
    }

    @Test
    void testCompressMemory() {
        // Given - 创建多条记忆
        StringBuilder memory = new StringBuilder();
        memory.append("【相关记忆】\n");
        for (int i = 0; i < 10; i++) {
            memory.append("- 记忆条目 ").append(i).append(": ").append("这是记忆内容。".repeat(10)).append("\n");
        }

        List<ContextSection> sections = new ArrayList<>();
        sections.add(ContextSection.builder()
                .id("memory")
                .content(memory.toString().trim())
                .type(ContextSection.SectionType.MEMORY)
                .priority(7)
                .build());

        ContextAllocation allocation = budgetManager.allocate(sections);

        // When
        List<ContextSection> compressed = compressor.compress(sections, allocation);

        // Then
        assertEquals(1, compressed.size());
        assertTrue(budgetManager.estimateTokens(compressed.get(0).getContent())
                <= allocation.getBudget(sections.get(0)));
    }

    @Test
    void testCompressMixedSections() {
        // Given
        List<ContextSection> sections = new ArrayList<>();

        // 系统提示词（固定）
        sections.add(ContextSection.builder()
                .id("system")
                .content("系统提示词")
                .type(ContextSection.SectionType.SYSTEM_PROMPT)
                .priority(10)
                .fixed(true)
                .build());

        // RAG 结果（需要压缩）
        sections.add(ContextSection.builder()
                .id("rag")
                .content("RAG 内容。".repeat(100))
                .type(ContextSection.SectionType.RAG_RESULT)
                .priority(8)
                .build());

        // 用户输入（固定）
        sections.add(ContextSection.builder()
                .id("user")
                .content("用户问题")
                .type(ContextSection.SectionType.USER_INPUT)
                .priority(10)
                .fixed(true)
                .build());

        ContextAllocation allocation = budgetManager.allocate(sections);

        // When
        List<ContextSection> compressed = compressor.compress(sections, allocation);

        // Then
        assertEquals(3, compressed.size());
        // 固定片段不应被压缩
        assertEquals("系统提示词", compressed.get(0).getContent());
        assertEquals("用户问题", compressed.get(2).getContent());
    }

    @Test
    void testDisabledCompressor() throws Exception {
        // Given
        var enabledField = ContextCompressor.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(compressor, false);

        List<ContextSection> sections = new ArrayList<>();
        sections.add(ContextSection.builder()
                .id("test")
                .content("内容")
                .type(ContextSection.SectionType.RAG_RESULT)
                .build());

        ContextAllocation allocation = budgetManager.allocate(sections);

        // When
        List<ContextSection> compressed = compressor.compress(sections, allocation);

        // Then
        assertEquals(sections, compressed);
    }
}
