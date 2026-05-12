package org.example.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextAwareRagService 单元测试
 */
class ContextAwareRagServiceTest {

    private ContextAwareRagService service;

    @BeforeEach
    void setUp() {
        service = new ContextAwareRagService();

        // 使用反射设置私有字段
        try {
            var enabledField = ContextAwareRagService.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(service, true);

            var rewriteEnabledField = ContextAwareRagService.class.getDeclaredField("rewriteEnabled");
            rewriteEnabledField.setAccessible(true);
            rewriteEnabledField.set(service, true);

            var entityExtractionEnabledField = ContextAwareRagService.class.getDeclaredField("entityExtractionEnabled");
            entityExtractionEnabledField.setAccessible(true);
            entityExtractionEnabledField.set(service, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testRewriteQueryWithoutChatModel() {
        // Given
        String query = "它的价格呢？";
        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(query)
                .recentMessages(List.of(
                        Map.of("role", "user", "content", "iPhone 15 有什么特点？"),
                        Map.of("role", "assistant", "content", "iPhone 15 有 A16 芯片...")
                ))
                .build();

        // When - 没有 ChatModel 时应该返回原查询
        String result = service.rewriteQuery(query, context);

        // Then
        assertEquals(query, result);
    }

    @Test
    void testExtractEntitiesWithoutChatModel() {
        // Given
        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery("问题")
                .recentMessages(List.of(
                        Map.of("role", "user", "content", "payment-service 的 CPU 使用率很高"),
                        Map.of("role", "assistant", "content", "让我检查一下...")
                ))
                .build();

        // When - 没有 ChatModel 时应该返回空列表
        List<String> result = service.extractEntities(context);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBuildEnhancedContextWithEntities() {
        // Given
        String ragContext = "这是 RAG 检索结果";
        List<String> entities = List.of("payment-service", "CPU", "Prometheus");

        // When
        String result = service.buildEnhancedContext(ragContext, entities);

        // Then
        assertNotNull(result);
        assertTrue(result.contains(ragContext));
        assertTrue(result.contains("payment-service"));
        assertTrue(result.contains("CPU"));
        assertTrue(result.contains("Prometheus"));
    }

    @Test
    void testBuildEnhancedContextWithoutEntities() {
        // Given
        String ragContext = "这是 RAG 检索结果";
        List<String> entities = List.of();

        // When
        String result = service.buildEnhancedContext(ragContext, entities);

        // Then
        assertEquals(ragContext, result);
    }

    @Test
    void testBuildEnhancedContextWithNullEntities() {
        // Given
        String ragContext = "这是 RAG 检索结果";

        // When
        String result = service.buildEnhancedContext(ragContext, null);

        // Then
        assertEquals(ragContext, result);
    }

    @Test
    void testProcessWithoutChatModel() {
        // Given
        String query = "问题";
        String ragContext = "RAG 内容";
        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(query)
                .build();

        // When
        ContextAwareRagService.ContextAwareRagResult result = service.process(query, ragContext, context);

        // Then
        assertNotNull(result);
        assertEquals(query, result.getRewrittenQuery());
        assertTrue(result.getEntities().isEmpty());
        assertEquals(ragContext, result.getEnhancedContext());
    }

    @Test
    void testDisabledService() throws Exception {
        // Given
        var enabledField = ContextAwareRagService.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(service, false);

        String query = "问题";
        ConversationContext context = ConversationContext.builder()
                .sessionId("session-1")
                .userId("user-1")
                .userQuery(query)
                .build();

        // When
        String rewritten = service.rewriteQuery(query, context);
        List<String> entities = service.extractEntities(context);

        // Then
        assertEquals(query, rewritten);
        assertTrue(entities.isEmpty());
    }

    @Test
    void testContextAwareRagResult() {
        // Given
        String rewrittenQuery = "改写后的查询";
        List<String> entities = List.of("entity1", "entity2");
        String enhancedContext = "增强的上下文";

        // When
        ContextAwareRagService.ContextAwareRagResult result =
                new ContextAwareRagService.ContextAwareRagResult(rewrittenQuery, entities, enhancedContext);

        // Then
        assertEquals(rewrittenQuery, result.getRewrittenQuery());
        assertEquals(entities, result.getEntities());
        assertEquals(enhancedContext, result.getEnhancedContext());
    }
}
