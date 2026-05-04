package org.example.memory;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;
import org.example.service.VectorEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * M3 修复验证：多语言关键词 + 高价值模式加分的重要性评估
 */
@ExtendWith(MockitoExtension.class)
class LongTermMemoryManagerImportanceTest {

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
        setField(manager, "saveThreshold", 1);       // 降低阈值以便测试
        setField(manager, "importanceThreshold", 0.7f);
        setField(manager, "contentMaxLength", 8192);
    }

    // ==================== 中文关键词测试 ====================

    @Test
    void evaluateImportance_shouldDetectChineseKeywords() {
        // 包含 "问题" 和 "解决" 的对话应通过阈值
        // 长度得分: 10 pairs → min(10/10, 0.5) = 0.5
        // 关键词: "问题" +0.1, "解决" +0.1 = 0.7
        List<Map<String, String>> history = buildHistory(10,
                "这是一个问题需要解决", "好的，我来帮你解决这个问题");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        // 验证被保存了（importance >= 0.7）
        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    // ==================== 英文关键词测试 ====================

    @Test
    void evaluateImportance_shouldDetectEnglishKeywords() {
        // 英文关键词 "error" 和 "fix" 应被检测到
        // 长度得分: 10 pairs → 0.5
        // 关键词: "error" +0.1, "fix" +0.1 = 0.7
        List<Map<String, String>> history = buildHistory(10,
                "There is an error in the system", "Let me fix this error");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    @Test
    void evaluateImportance_shouldBeCaseInsensitive() {
        // 大小写混合的英文关键词应被检测到
        // 长度得分: 10 pairs → 0.5
        // 关键词: "ERROR"→"error" +0.1, "CRITICAL"→"critical" +0.1 = 0.7
        List<Map<String, String>> history = buildHistory(10,
                "CRITICAL issue found", "ERROR log shows the problem");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    // ==================== 技术关键词测试 ====================

    @Test
    void evaluateImportance_shouldDetectTechnicalKeywords() {
        // 技术关键词 "timeout" 和 "exception" 应被检测到
        // 长度得分: 10 pairs → 0.5
        // 关键词: "timeout" +0.1, "exception" +0.1 = 0.7
        List<Map<String, String>> history = buildHistory(10,
                "Connection timeout detected", "java.net.SocketTimeoutException thrown");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    // ==================== 高价值模式测试 ====================

    @Test
    void evaluateImportance_shouldBonusForRootCauseAnalysis() {
        // 包含 "根因" 的对话应获得额外加分
        // 长度得分: 5 pairs → 0.5
        // 关键词: 无命中 = 0
        // 高价值: "根因" +0.2 = 0.7
        List<Map<String, String>> history = buildHistory(5,
                "经过分析，根因是内存泄漏", "明白了，需要修复内存管理");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    @Test
    void evaluateImportance_shouldBonusForRootCauseEnglish() {
        // "root cause" 英文模式也应获得加分
        // 长度得分: 5 pairs → 0.5
        // 高价值: "root cause" +0.2 = 0.7
        List<Map<String, String>> history = buildHistory(5,
                "The root cause is a memory leak", "We need to fix the memory management");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    // ==================== 不通过阈值测试 ====================

    @Test
    void evaluateImportance_shouldRejectLowImportanceContent() {
        // 短对话且无关键词 → 不通过阈值
        // 长度得分: 2 pairs → min(2/10, 0.5) = 0.2
        // 无关键词命中 = 0
        // 总分 = 0.2 < 0.7
        List<Map<String, String>> history = buildHistory(2,
                "hi", "hello how are you");

        manager.saveConversation("s1", history);

        // 不应调用 embedding 和 milvus
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(milvusClient);
    }

    @Test
    void evaluateImportance_shouldCapScoreAtOne() {
        // 大量关键词命中 + 长对话 → 分数应封顶在 1.0
        // 不应因为分数超过 1.0 而出错
        List<Map<String, String>> history = buildHistory(20,
                "问题 错误 故障 优化 建议 重要 注意 issue fix error bug critical timeout OOM 根因",
                "解决 resolved CPU memory disk latency exception root cause");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        // 应该被保存（importance >= 0.7），不应抛出异常
        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    // ==================== 混合语言测试 ====================

    @Test
    void evaluateImportance_shouldHandleMixedLanguageContent() {
        // 中英文混合的运维对话
        // 长度得分: 10 pairs → 0.5
        // 关键词: "error" +0.1, "故障" +0.1 = 0.7
        List<Map<String, String>> history = buildHistory(10,
                "系统出现 error，故障码 500", "排查发现是 DB connection timeout");

        mockEmbeddingAndInsert();
        manager.saveConversation("s1", history);

        verify(milvusClient, times(1)).insert(any(InsertParam.class));
    }

    // ==================== 辅助方法 ====================

    private List<Map<String, String>> buildHistory(int pairs, String userContent, String assistantContent) {
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            history.add(Map.of("role", "user", "content", userContent));
            history.add(Map.of("role", "assistant", "content", assistantContent));
        }
        return history;
    }

    private void mockEmbeddingAndInsert() {
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 1024; i++) vector.add(0.1f);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(vector);

        R<MutationResult> successR = new R<>();
        successR.setStatus(0);
        successR.setData(MutationResult.newBuilder().build());
        when(milvusClient.insert(any(InsertParam.class))).thenReturn(successR);
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
