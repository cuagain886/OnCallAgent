package org.example.memory;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import org.example.constant.MilvusConstants;
import org.example.service.VectorEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * H4 修复验证：独立 memory 集合 + session_id 原生过滤 + loadCollection 缓存
 */
@ExtendWith(MockitoExtension.class)
class LongTermMemoryManagerTest {

    @Mock
    private MilvusServiceClient milvusClient;

    @Mock
    private VectorEmbeddingService embeddingService;

    private LongTermMemoryManager manager;

    @BeforeEach
    void setUp() {
        manager = new LongTermMemoryManager();
        setField(manager, "milvusClient", milvusClient);
        setField(manager, "embeddingService", embeddingService);
        setField(manager, "enableAutoSave", true);
        setField(manager, "saveThreshold", 3);
        setField(manager, "importanceThreshold", 0.7f);
        setField(manager, "contentMaxLength", 8192);
    }

    // ==================== H4: 使用独立 memory 集合 ====================

    @Test
    void saveMemoryContent_shouldUseMemoryCollection() {
        // 准备：模拟 embedding 服务返回向量
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 1024; i++) vector.add(0.1f);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(vector);

        // 模拟 Milvus 插入成功
        R<MutationResult> successR = new R<>();
        successR.setStatus(0);
        successR.setData(MutationResult.newBuilder().build());
        when(milvusClient.insert(any(InsertParam.class))).thenReturn(successR);

        // 构造足够长的历史以通过重要性阈值
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(Map.of("role", "user", "content", "问题" + i));
            history.add(Map.of("role", "assistant", "content", "解决" + i));
        }

        // 执行
        manager.saveConversation("session-abc", history);

        // 验证：插入时使用的是 MEMORY_COLLECTION_NAME（"memory"），而非 MILVUS_COLLECTION_NAME（"biz"）
        ArgumentCaptor<InsertParam> captor = ArgumentCaptor.forClass(InsertParam.class);
        verify(milvusClient).insert(captor.capture());
        InsertParam insertParam = captor.getValue();

        assertEquals(MilvusConstants.MEMORY_COLLECTION_NAME, insertParam.getCollectionName(),
                "Should use dedicated 'memory' collection, not shared 'biz' collection");
    }

    @Test
    void saveMemoryContent_shouldIncludeSessionIdField() {
        // 准备
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 1024; i++) vector.add(0.1f);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(vector);

        R<MutationResult> successR = new R<>();
        successR.setStatus(0);
        successR.setData(MutationResult.newBuilder().build());
        when(milvusClient.insert(any(InsertParam.class))).thenReturn(successR);

        List<Map<String, String>> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(Map.of("role", "user", "content", "问题" + i));
            history.add(Map.of("role", "assistant", "content", "解决" + i));
        }

        // 执行
        manager.saveConversation("session-xyz", history);

        // 验证：插入的字段中包含 session_id
        ArgumentCaptor<InsertParam> captor = ArgumentCaptor.forClass(InsertParam.class);
        verify(milvusClient).insert(captor.capture());
        InsertParam insertParam = captor.getValue();

        boolean hasSessionIdField = insertParam.getFields().stream()
                .anyMatch(field -> field.getName().equals(MilvusConstants.MEMORY_SESSION_ID_FIELD));
        assertTrue(hasSessionIdField, "Insert should include 'session_id' field for native filtering");

        // 验证 session_id 的值
        insertParam.getFields().stream()
                .filter(f -> f.getName().equals(MilvusConstants.MEMORY_SESSION_ID_FIELD))
                .findFirst()
                .ifPresent(f -> {
                    List<?> values = f.getValues();
                    assertEquals("session-xyz", values.get(0));
                });
    }

    // ==================== H4: Milvus 原生表达式过滤 ====================

    @Test
    void retrieveRelevantMemoriesBySession_shouldUseExprFilter() {
        // 准备
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 1024; i++) vector.add(0.1f);
        when(embeddingService.generateQueryVector(anyString())).thenReturn(vector);

        // 模拟 Milvus 返回空结果
        R<SearchResults> emptyR = new R<>();
        emptyR.setStatus(0);
        emptyR.setData(SearchResults.newBuilder().build());
        when(milvusClient.search(any(SearchParam.class))).thenReturn(emptyR);

        // 执行
        manager.retrieveRelevantMemoriesBySession("test query", 3, "session-abc");

        // 验证：搜索时使用了 withExpr 进行原生过滤
        ArgumentCaptor<SearchParam> captor = ArgumentCaptor.forClass(SearchParam.class);
        verify(milvusClient).search(captor.capture());
        SearchParam searchParam = captor.getValue();

        // 验证使用 memory 集合
        assertEquals(MilvusConstants.MEMORY_COLLECTION_NAME, searchParam.getCollectionName(),
                "Should search in dedicated 'memory' collection");

        // 验证包含 expr 过滤条件
        assertNotNull(searchParam.getExpr(), "Search should include expression filter");
        assertTrue(searchParam.getExpr().contains("session_id"),
                "Expression should filter by session_id");
        assertTrue(searchParam.getExpr().contains("session-abc"),
                "Expression should contain the target session ID");
    }

    @Test
    void retrieveRelevantMemoriesBySession_shouldReturnEmptyForNullSessionId() {
        List<LongTermMemoryManager.Memory> result = manager.retrieveRelevantMemoriesBySession("query", 3, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void retrieveRelevantMemoriesBySession_shouldReturnEmptyForBlankSessionId() {
        List<LongTermMemoryManager.Memory> result = manager.retrieveRelevantMemoriesBySession("query", 3, "  ");
        assertTrue(result.isEmpty());
    }

    // ==================== loadCollection 缓存测试 ====================

    @Test
    void init_shouldLoadMemoryCollectionAtStartup() {
        // 模拟 loadCollection 成功（status=0）
        // 使用 doReturn 避免泛型类型检查
        doReturn(buildRpcStatusR(0)).when(milvusClient).loadCollection(any(LoadCollectionParam.class));

        // 执行 @PostConstruct
        manager.init();

        // 验证：loadCollection 被调用，且使用 MEMORY_COLLECTION_NAME
        ArgumentCaptor<LoadCollectionParam> captor = ArgumentCaptor.forClass(LoadCollectionParam.class);
        verify(milvusClient).loadCollection(captor.capture());
        assertEquals(MilvusConstants.MEMORY_COLLECTION_NAME, captor.getValue().getCollectionName());
    }

    @Test
    void init_shouldHandleAlreadyLoadedCollection() {
        // 模拟 collection 已加载（status = 65535）
        doReturn(buildRpcStatusR(65535)).when(milvusClient).loadCollection(any(LoadCollectionParam.class));

        // 不应抛出异常
        assertDoesNotThrow(() -> manager.init());
    }

    @Test
    void init_shouldHandleLoadFailureGracefully() {
        // 模拟 loadCollection 抛出异常
        when(milvusClient.loadCollection(any(LoadCollectionParam.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // 不应抛出异常（graceful degradation）
        assertDoesNotThrow(() -> manager.init());
    }

    // ==================== 重要性阈值测试 ====================

    @Test
    void saveConversation_shouldSkipWhenBelowThreshold() {
        // 历史太短（< saveThreshold * 2 = 6 条消息）
        List<Map<String, String>> shortHistory = List.of(
                Map.of("role", "user", "content", "hi"),
                Map.of("role", "assistant", "content", "hello")
        );

        manager.saveConversation("s1", shortHistory);

        // 不应该调用 embedding 和 milvus
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(milvusClient);
    }

    @Test
    void saveConversation_shouldSkipWhenAutoSaveDisabled() {
        setField(manager, "enableAutoSave", false);

        List<Map<String, String>> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(Map.of("role", "user", "content", "问题" + i));
            history.add(Map.of("role", "assistant", "content", "解决" + i));
        }

        manager.saveConversation("s1", history);

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(milvusClient);
    }

    // ==================== 辅助方法 ====================

    private R<RpcStatus> buildRpcStatusR(int status) {
        R<RpcStatus> r = new R<>();
        r.setStatus(status);
        return r;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
