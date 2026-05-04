package org.example.Hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import org.example.memory.MemoryTransformer;
import org.example.memory.ShortTermMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * H2 修复验证：Session 级别串行化队列，保证同一会话的消息按序写入
 */
@ExtendWith(MockitoExtension.class)
class MemoryPersistHookTest {

    @Mock
    private ShortTermMemoryManager shortTermMemoryManager;

    @Mock
    private MemoryTransformer memoryTransformer;

    private MemoryPersistHook hook;

    @BeforeEach
    void setUp() {
        hook = new MemoryPersistHook();
        setField(hook, "shortTermMemoryManager", shortTermMemoryManager);
        setField(hook, "memoryTransformer", memoryTransformer);
        setField(hook, "hooksEnabled", true);
        setField(hook, "asyncPersist", true);
    }

    // ==================== H2: 串行化队列测试 ====================

    @Test
    void afterModel_shouldSerializeAsyncPersistForSameSession() throws Exception {
        // 记录 addMessage 的调用顺序
        AtomicInteger callOrder = new AtomicInteger(0);
        List<String> callSequence = Collections.synchronizedList(new ArrayList<>());

        doAnswer(invocation -> {
            String role = invocation.getArgument(1);
            String content = invocation.getArgument(2);
            int order = callOrder.incrementAndGet();
            callSequence.add(order + ":" + role + ":" + content);
            return null;
        }).when(shortTermMemoryManager).addMessage(anyString(), anyString(), anyString());

        // 模拟两次并发请求，同一个 sessionId
        OverAllState state1 = buildState("Q1", "A1");
        OverAllState state2 = buildState("Q2", "A2");
        RunnableConfig config = buildConfig("same-session");

        // 同时触发两次 afterModel
        CompletableFuture<Map<String, Object>> f1 = hook.afterModel(state1, config);
        CompletableFuture<Map<String, Object>> f2 = hook.afterModel(state2, config);

        // 等待两个都完成
        CompletableFuture.allOf(f1, f2).get();

        // 等待异步任务完成
        Thread.sleep(500);

        // 验证：addMessage 被调用了 4 次（2 次 user + 2 次 assistant）
        verify(shortTermMemoryManager, times(4)).addMessage(eq("same-session"), anyString(), anyString());

        // 验证：对于同一 session，Q1/A1 应该在 Q2/A2 之前（串行化保证）
        assertTrue(callSequence.size() == 4, "Should have 4 calls");

        int q1Index = -1, a1Index = -1, q2Index = -1, a2Index = -1;
        for (int i = 0; i < callSequence.size(); i++) {
            String call = callSequence.get(i);
            if (call.contains("Q1")) q1Index = i;
            if (call.contains("A1")) a1Index = i;
            if (call.contains("Q2")) q2Index = i;
            if (call.contains("A2")) a2Index = i;
        }

        assertTrue(q1Index < a1Index, "Q1 should be before A1 (same request pair)");
        assertTrue(q1Index < q2Index, "Q1 should be before Q2 (serial queue)");
        assertTrue(a1Index < a2Index, "A1 should be before A2 (serial queue)");
    }

    @Test
    void afterModel_shouldHandleDifferentSessionsIndependently() throws Exception {
        doNothing().when(shortTermMemoryManager).addMessage(anyString(), anyString(), anyString());

        OverAllState state1 = buildState("Q1", "A1");
        OverAllState state2 = buildState("Q2", "A2");

        // 不同 session 可以并行执行
        CompletableFuture<Map<String, Object>> f1 = hook.afterModel(state1, buildConfig("session-1"));
        CompletableFuture<Map<String, Object>> f2 = hook.afterModel(state2, buildConfig("session-2"));

        CompletableFuture.allOf(f1, f2).get();
        Thread.sleep(500);

        // 两个 session 各自调用了 addMessage 2 次
        verify(shortTermMemoryManager, times(2)).addMessage(eq("session-1"), anyString(), anyString());
        verify(shortTermMemoryManager, times(2)).addMessage(eq("session-2"), anyString(), anyString());
    }

    // ==================== 基本功能测试 ====================

    @Test
    void afterModel_shouldPersistUserAndAssistantMessages() throws Exception {
        doNothing().when(shortTermMemoryManager).addMessage(anyString(), anyString(), anyString());

        OverAllState state = buildState("如何排查 CPU 问题？", "CPU 排查步骤如下...");
        RunnableConfig config = buildConfig("session-abc");

        hook.afterModel(state, config).get();
        Thread.sleep(300);

        // 验证调用了 addMessage 两次：user 和 assistant
        verify(shortTermMemoryManager).addMessage("session-abc", "user", "如何排查 CPU 问题？");
        verify(shortTermMemoryManager).addMessage("session-abc", "assistant", "CPU 排查步骤如下...");
        verify(memoryTransformer).transformToLongTerm("session-abc");
    }

    @Test
    void afterModel_shouldReturnEmpty_whenHooksDisabled() throws Exception {
        setField(hook, "hooksEnabled", false);

        OverAllState state = buildState("Q1", "A1");
        RunnableConfig config = buildConfig("session-abc");

        CompletableFuture<Map<String, Object>> result = hook.afterModel(state, config);

        assertTrue(result.get().isEmpty());
        verifyNoInteractions(shortTermMemoryManager);
    }

    @Test
    void afterModel_shouldReturnEmpty_whenSessionIdIsNull() throws Exception {
        OverAllState state = buildState("Q1", "A1");
        RunnableConfig config = RunnableConfig.builder().build();  // 无 sessionId

        CompletableFuture<Map<String, Object>> result = hook.afterModel(state, config);

        assertTrue(result.get().isEmpty());
        verifyNoInteractions(shortTermMemoryManager);
    }

    @Test
    void afterModel_shouldReturnEmpty_whenNoValidMessagePair() throws Exception {
        // 空的 state，没有消息
        OverAllState state = new OverAllState();
        RunnableConfig config = buildConfig("session-abc");

        CompletableFuture<Map<String, Object>> result = hook.afterModel(state, config);

        assertTrue(result.get().isEmpty());
        verifyNoInteractions(shortTermMemoryManager);
    }

    @Test
    void afterModel_shouldPersistSynchronously_whenAsyncDisabled() throws Exception {
        setField(hook, "asyncPersist", false);
        doNothing().when(shortTermMemoryManager).addMessage(anyString(), anyString(), anyString());

        OverAllState state = buildState("Q1", "A1");
        RunnableConfig config = buildConfig("session-abc");

        hook.afterModel(state, config).get();

        // 同步模式下，addMessage 应该已经被调用
        verify(shortTermMemoryManager).addMessage("session-abc", "user", "Q1");
        verify(shortTermMemoryManager).addMessage("session-abc", "assistant", "A1");
    }

    // ==================== Hook 元数据测试 ====================

    @Test
    void hook_shouldHaveCorrectName() {
        assertEquals("memory_persist", hook.getName());
    }

    @Test
    void hook_shouldHaveAfterModelPosition() {
        HookPosition[] positions = hook.getHookPositions();
        assertEquals(1, positions.length);
        assertEquals(HookPosition.AFTER_MODEL, positions[0]);
    }

    // ==================== 辅助方法 ====================

    private OverAllState buildState(String userQuestion, String assistantAnswer) {
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userQuestion));
        messages.add(new AssistantMessage(assistantAnswer));

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
