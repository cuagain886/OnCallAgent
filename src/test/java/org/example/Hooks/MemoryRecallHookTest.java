package org.example.Hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import org.example.memory.LongTermMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * H1 修复验证：移除全局降级召回，防止跨会话记忆污染
 */
@ExtendWith(MockitoExtension.class)
class MemoryRecallHookTest {

    @Mock
    private LongTermMemoryManager longTermMemoryManager;

    private MemoryRecallHook hook;

    @BeforeEach
    void setUp() {
        hook = new MemoryRecallHook();
        setField(hook, "longTermMemoryManager", longTermMemoryManager);
        setField(hook, "hooksEnabled", true);
        setField(hook, "topK", 3);
        setField(hook, "maxContextLength", 1500);
    }

    // ==================== H1: 无全局降级召回 ====================

    @Test
    void beforeModel_shouldNotFallBackToGlobalRecall() throws Exception {
        // 模拟 session 内召回返回空
        when(longTermMemoryManager.retrieveRelevantMemoriesBySession(anyString(), eq(3), eq("session-abc")))
                .thenReturn(Collections.emptyList());

        // 构建 state 和 config
        OverAllState state = buildStateWithMessages("如何排查 CPU 问题？");
        RunnableConfig config = buildConfig("session-abc");

        // 执行
        CompletableFuture<Map<String, Object>> result = hook.beforeModel(state, config);

        // 验证：只调用了 retrieveRelevantMemoriesBySession，没有调用 retrieveRelevantMemories（全局）
        verify(longTermMemoryManager).retrieveRelevantMemoriesBySession("如何排查 CPU 问题？", 3, "session-abc");
        verify(longTermMemoryManager, never()).retrieveRelevantMemories(anyString(), anyInt());

        // 返回空 map（不注入任何上下文）
        assertTrue(result.get().isEmpty());
    }

    @Test
    void beforeModel_shouldInjectContextWhenSessionMemoriesExist() throws Exception {
        // 模拟 session 内召回有结果
        LongTermMemoryManager.Memory memory = new LongTermMemoryManager.Memory();
        memory.setId("mem-1");
        memory.setContent("之前讨论过 CPU 高负载排查方法");
        memory.setScore(0.85f);

        when(longTermMemoryManager.retrieveRelevantMemoriesBySession(anyString(), eq(3), eq("session-abc")))
                .thenReturn(List.of(memory));

        OverAllState state = buildStateWithMessages("继续上次的排查");
        RunnableConfig config = buildConfig("session-abc");

        CompletableFuture<Map<String, Object>> result = hook.beforeModel(state, config);

        // 验证：返回了包含 messages 的 map
        Map<String, Object> resultMap = result.get();
        assertTrue(resultMap.containsKey("messages"));

        // 验证注入的 messages 中包含召回上下文
        Object messagesObj = resultMap.get("messages");
        assertTrue(messagesObj instanceof ReplaceAllWith);
        @SuppressWarnings("unchecked")
        List<Message> messages = ((ReplaceAllWith<Message>) messagesObj).newValues();

        boolean hasRecallContext = messages.stream()
                .filter(m -> m instanceof SystemMessage)
                .map(m -> ((SystemMessage) m).getText())
                .anyMatch(text -> text.contains("CPU 高负载排查"));
        assertTrue(hasRecallContext, "Should inject recall context into system message");
    }

    @Test
    void beforeModel_shouldReturnEmpty_whenHooksDisabled() throws Exception {
        setField(hook, "hooksEnabled", false);

        OverAllState state = buildStateWithMessages("test");
        RunnableConfig config = buildConfig("session-abc");

        CompletableFuture<Map<String, Object>> result = hook.beforeModel(state, config);

        assertTrue(result.get().isEmpty());
        verifyNoInteractions(longTermMemoryManager);
    }

    @Test
    void beforeModel_shouldReturnEmpty_whenSessionIdIsNull() throws Exception {
        OverAllState state = buildStateWithMessages("test");
        // 不设置 threadId 和 sessionId metadata
        RunnableConfig config = RunnableConfig.builder().build();

        CompletableFuture<Map<String, Object>> result = hook.beforeModel(state, config);

        assertTrue(result.get().isEmpty());
        verifyNoInteractions(longTermMemoryManager);
    }

    @Test
    void beforeModel_shouldReturnEmpty_whenQuestionIsBlank() throws Exception {
        OverAllState state = buildStateWithMessages("  ");
        RunnableConfig config = buildConfig("session-abc");

        CompletableFuture<Map<String, Object>> result = hook.beforeModel(state, config);

        assertTrue(result.get().isEmpty());
        verifyNoInteractions(longTermMemoryManager);
    }

    @Test
    void beforeModel_shouldReturnEmpty_whenNoMessages() throws Exception {
        OverAllState state = new OverAllState();
        RunnableConfig config = buildConfig("session-abc");

        CompletableFuture<Map<String, Object>> result = hook.beforeModel(state, config);

        assertTrue(result.get().isEmpty());
        verifyNoInteractions(longTermMemoryManager);
    }

    @Test
    void beforeModel_shouldGracefullyDegradeOnException() throws Exception {
        when(longTermMemoryManager.retrieveRelevantMemoriesBySession(anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("Milvus connection failed"));

        OverAllState state = buildStateWithMessages("test question");
        RunnableConfig config = buildConfig("session-abc");

        CompletableFuture<Map<String, Object>> result = hook.beforeModel(state, config);

        // 异常被 catch，返回空 map
        assertTrue(result.get().isEmpty());
    }

    // ==================== Hook 元数据测试 ====================

    @Test
    void hook_shouldHaveCorrectName() {
        assertEquals("memory_recall", hook.getName());
    }

    @Test
    void hook_shouldHaveBeforeModelPosition() {
        HookPosition[] positions = hook.getHookPositions();
        assertEquals(1, positions.length);
        assertEquals(HookPosition.BEFORE_MODEL, positions[0]);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建包含 messages 的 OverAllState。
     * OverAllState 没有 builder，通过构造函数传入初始数据。
     */
    private OverAllState buildStateWithMessages(String userQuestion) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个智能助手"));
        messages.add(new UserMessage(userQuestion));

        OverAllState state = new OverAllState();
        state.updateState(Map.of("messages", messages));
        return state;
    }

    private RunnableConfig buildConfig(String sessionId) {
        return RunnableConfig.builder()
                .threadId(sessionId)
                .addMetadata("sessionId", sessionId)
                .build();
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
