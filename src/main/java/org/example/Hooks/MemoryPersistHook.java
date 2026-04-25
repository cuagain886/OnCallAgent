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

        Runnable persistTask = () -> persist(sessionId, pair);
        if (asyncPersist) {
            CompletableFuture.runAsync(persistTask)
                    .exceptionally(ex -> {
                        log.warn("MemoryPersistHook 异步持久化失败: {}", ex.getMessage());
                        return null;
                    });
        } else {
            try {
                persistTask.run();
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
