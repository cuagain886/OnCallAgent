package org.example.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenBudgetManager 单元测试
 */
class TokenBudgetManagerTest {

    private TokenBudgetManager manager;

    @BeforeEach
    void setUp() {
        manager = new TokenBudgetManager();

        // 使用反射设置私有字段
        try {
            var enabledField = TokenBudgetManager.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(manager, true);

            var maxTokensField = TokenBudgetManager.class.getDeclaredField("maxTokens");
            maxTokensField.setAccessible(true);
            maxTokensField.set(manager, 8000);

            var systemPromptWeightField = TokenBudgetManager.class.getDeclaredField("systemPromptWeight");
            systemPromptWeightField.setAccessible(true);
            systemPromptWeightField.set(manager, 0.15);

            var ragWeightField = TokenBudgetManager.class.getDeclaredField("ragWeight");
            ragWeightField.setAccessible(true);
            ragWeightField.set(manager, 0.25);

            var memoryWeightField = TokenBudgetManager.class.getDeclaredField("memoryWeight");
            memoryWeightField.setAccessible(true);
            memoryWeightField.set(manager, 0.15);

            var conversationWeightField = TokenBudgetManager.class.getDeclaredField("conversationWeight");
            conversationWeightField.setAccessible(true);
            conversationWeightField.set(manager, 0.30);

            var userInputWeightField = TokenBudgetManager.class.getDeclaredField("userInputWeight");
            userInputWeightField.setAccessible(true);
            userInputWeightField.set(manager, 0.15);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testEstimateTokensEmpty() {
        assertEquals(0, manager.estimateTokens(null));
        assertEquals(0, manager.estimateTokens(""));
    }

    @Test
    void testEstimateTokensChinese() {
        // 中文字符约 1.5 token/字符
        int tokens = manager.estimateTokens("你好世界");
        assertTrue(tokens > 0);
        assertTrue(tokens <= 10); // 4 字符 * 1.5 = 6
    }

    @Test
    void testEstimateTokensEnglish() {
        // 英文单词约 1 token/词
        int tokens = manager.estimateTokens("Hello World");
        assertTrue(tokens > 0);
        assertTrue(tokens <= 5); // 2 词 * 1 = 2
    }

    @Test
    void testEstimateTokensMixed() {
        // 混合文本
        int tokens = manager.estimateTokens("Hello 你好 World");
        assertTrue(tokens > 0);
    }

    @Test
    void testTruncateToTokensNoTruncation() {
        String text = "Short text";
        String result = manager.truncateToTokens(text, 1000);
        assertEquals(text, result);
    }

    @Test
    void testTruncateToTokensWithTruncation() {
        String text = "这是一段很长的文本，需要被截断。".repeat(100);
        String result = manager.truncateToTokens(text, 10);
        assertTrue(result.length() < text.length());
        assertTrue(result.endsWith("...(truncated)"));
    }

    @Test
    void testAllocateSimple() {
        List<ContextSection> sections = new ArrayList<>();

        sections.add(ContextSection.builder()
                .id("system")
                .content("系统提示词内容")
                .type(ContextSection.SectionType.SYSTEM_PROMPT)
                .priority(10)
                .fixed(true)
                .build());

        sections.add(ContextSection.builder()
                .id("rag")
                .content("RAG 检索结果内容")
                .type(ContextSection.SectionType.RAG_RESULT)
                .priority(8)
                .build());

        sections.add(ContextSection.builder()
                .id("user")
                .content("用户输入内容")
                .type(ContextSection.SectionType.USER_INPUT)
                .priority(10)
                .fixed(true)
                .build());

        ContextAllocation allocation = manager.allocate(sections);

        assertNotNull(allocation);
        assertEquals(8000, allocation.getTotalBudget());
        assertTrue(allocation.getUsedTokens() > 0);
        assertFalse(allocation.isOverBudget());
    }

    @Test
    void testAllocateRespectsPriority() {
        List<ContextSection> sections = new ArrayList<>();

        // 高优先级片段
        sections.add(ContextSection.builder()
                .id("high")
                .content("高优先级内容")
                .type(ContextSection.SectionType.RAG_RESULT)
                .priority(10)
                .build());

        // 低优先级片段
        sections.add(ContextSection.builder()
                .id("low")
                .content("低优先级内容")
                .type(ContextSection.SectionType.CONVERSATION)
                .priority(3)
                .build());

        ContextAllocation allocation = manager.allocate(sections);

        int highBudget = allocation.getBudget(sections.get(0));
        int lowBudget = allocation.getBudget(sections.get(1));

        // 高优先级应该获得更多预算
        assertTrue(highBudget >= lowBudget);
    }

    @Test
    void testAllocateFixedSections() {
        List<ContextSection> sections = new ArrayList<>();

        sections.add(ContextSection.builder()
                .id("fixed")
                .content("固定内容")
                .type(ContextSection.SectionType.SYSTEM_PROMPT)
                .priority(10)
                .fixed(true)
                .build());

        sections.add(ContextSection.builder()
                .id("dynamic")
                .content("动态内容")
                .type(ContextSection.SectionType.RAG_RESULT)
                .priority(8)
                .build());

        ContextAllocation allocation = manager.allocate(sections);

        // 固定片段应该获得完整预算
        int fixedBudget = allocation.getBudget(sections.get(0));
        int fixedTokens = manager.estimateTokens(sections.get(0).getContent());
        assertTrue(fixedBudget >= fixedTokens);
    }

    @Test
    void testTruncateSections() {
        List<ContextSection> sections = new ArrayList<>();

        String longContent = "这是一段很长的内容。".repeat(100);
        sections.add(ContextSection.builder()
                .id("long")
                .content(longContent)
                .type(ContextSection.SectionType.RAG_RESULT)
                .priority(8)
                .build());

        ContextAllocation allocation = manager.allocate(sections);
        List<ContextSection> truncated = manager.truncateSections(sections, allocation);

        assertEquals(1, truncated.size());
        assertTrue(manager.estimateTokens(truncated.get(0).getContent())
                <= allocation.getBudget(sections.get(0)));
    }

    @Test
    void testDisabledManager() throws Exception {
        var enabledField = TokenBudgetManager.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(manager, false);

        List<ContextSection> sections = new ArrayList<>();
        sections.add(ContextSection.builder()
                .id("test")
                .content("测试内容")
                .type(ContextSection.SectionType.RAG_RESULT)
                .build());

        ContextAllocation allocation = manager.allocate(sections);

        // 未启用时返回无限预算
        assertEquals(Integer.MAX_VALUE, allocation.getTotalBudget());
    }
}
