package org.example.Hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import lombok.extern.slf4j.Slf4j;
import org.example.memory.MemoryTransformer;
import org.example.memory.ShortTermMemoryManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在模型调用后将本轮对话写入短期记忆，并按阈值沉淀到长期记忆。
 */
@Slf4j
@Component
public class MemoryPersistHook extends ModelHook {

    @Autowired
    private ShortTermMemoryManager shortTermMemoryManager;

    @Autowired
    private MemoryTransformer memoryTransformer;

    @Value("${memory.hooks.enabled:true}")
    private boolean hooksEnabled;

    @Value("${memory.hooks.persist.async:true}")
    private boolean asyncPersist;

    // Session 级别的串行化队列，确保同一 sessionId 的持久化操作按序执行，
    // 避免并发请求导致 user/assistant 消息交错写入。
    private final ConcurrentHashMap<String, CompletableFuture<Void>> sessionPersistQueues = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "memory_persist";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.AFTER_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        if (!hooksEnabled) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        String sessionId = resolveSessionId(config);
        if (sessionId == null || sessionId.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        MessagePair pair = extractLatestPair(state);
        if (pair == null || pair.userQuestion.isBlank() || pair.assistantAnswer.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        if (asyncPersist) {
            // 使用 sessionId 级别的串行队列，保证同一会话的 user+assistant 写入顺序一致
            sessionPersistQueues.compute(sessionId, (key, prev) -> {
                CompletableFuture<Void> chain = (prev == null)
                        ? CompletableFuture.completedFuture(null)
                        : prev;
                return chain.thenRunAsync(() -> persist(sessionId, pair))
                        .exceptionally(ex -> {
                            log.warn("MemoryPersistHook 异步持久化失败: sessionId={}, error={}", sessionId, ex.getMessage());
                            return null;
                        });
            });
        } else {
            try {
                persist(sessionId, pair);
            } catch (Exception e) {
                log.warn("MemoryPersistHook 同步持久化失败: {}", e.getMessage());
            }
        }

        return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    private void persist(String sessionId, MessagePair pair) {
        shortTermMemoryManager.addMessage(sessionId, "user", pair.userQuestion);
        shortTermMemoryManager.addMessage(sessionId, "assistant", pair.assistantAnswer);
        memoryTransformer.transformToLongTerm(sessionId);
        log.debug("MemoryPersistHook 记忆持久化完成: sessionId={}", sessionId);
    }

    private String resolveSessionId(RunnableConfig config) {
        Optional<String> threadId = config.threadId();
        if (threadId.isPresent() && !threadId.get().isBlank()) {
            return threadId.get();
        }
        return config.metadata("sessionId")
                .map(String::valueOf)
                .filter(id -> !id.isBlank())
                .orElse(null);
    }

    private MessagePair extractLatestPair(OverAllState state) {
        List<Message> messages = readMessages(state);
        String latestAssistant = null;

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (latestAssistant == null && message instanceof AssistantMessage assistantMessage) {
                String text = assistantMessage.getText();
                if (text != null && !text.isBlank()) {
                    latestAssistant = text;
                }
                continue;
            }

            if (latestAssistant != null && message instanceof UserMessage userMessage) {
                String question = userMessage.getText();
                if (question != null && !question.isBlank()) {
                    return new MessagePair(question, latestAssistant);
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Message> readMessages(OverAllState state) {
        return (List<Message>) state.value("messages").orElseGet(ArrayList::new);
    }

    private static class MessagePair {
        private final String userQuestion;
        private final String assistantAnswer;

        private MessagePair(String userQuestion, String assistantAnswer) {
            this.userQuestion = userQuestion;
            this.assistantAnswer = assistantAnswer;
        }
    }
}
