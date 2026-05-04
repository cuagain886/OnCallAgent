package org.example.Hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import lombok.extern.slf4j.Slf4j;
import org.example.memory.LongTermMemoryManager;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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
 * 在模型调用前从长期记忆检索相关内容，并注入到系统上下文中。
 */
@Slf4j
@Component
public class MemoryRecallHook extends ModelHook {

    @Autowired
    private LongTermMemoryManager longTermMemoryManager;

    @Value("${memory.hooks.enabled:true}")
    private boolean hooksEnabled;

    @Value("${memory.hooks.recall.top-k:3}")
    private int topK;

    @Value("${memory.hooks.recall.max-context-length:1500}")
    private int maxContextLength;

    @Override
    public String getName() {
        return "memory_recall";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.BEFORE_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        if (!hooksEnabled) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        try {
            String sessionId = resolveSessionId(config);
            if (sessionId == null || sessionId.isBlank()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }

            String question = extractLatestUserQuestion(state);
            if (question == null || question.isBlank()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }

            List<LongTermMemoryManager.Memory> memories =
                    longTermMemoryManager.retrieveRelevantMemoriesBySession(question, topK, sessionId);

            // 已移除全局降级召回，防止跨会话记忆污染（其他用户的记忆被注入当前会话）。
            if (memories.isEmpty()) {
                log.debug("No session-specific memories found for session: {}", sessionId);
            }
            if (memories.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }

            String recallContext = buildRecallContext(memories);
            List<Message> updatedMessages = mergeIntoSystemMessage(state, recallContext);

            log.debug("MemoryRecallHook 注入记忆成功: sessionId={}, recalled={}", sessionId, memories.size());
            return CompletableFuture.completedFuture(Map.of("messages", ReplaceAllWith.of(updatedMessages)));
        } catch (Exception e) {
            log.warn("MemoryRecallHook 执行失败，已降级: {}", e.getMessage());
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
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

    private String extractLatestUserQuestion(OverAllState state) {
        List<Message> messages = readMessages(state);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof UserMessage userMessage) {
                return userMessage.getText();
            }
        }
        return null;
    }

    private List<Message> mergeIntoSystemMessage(OverAllState state, String recallContext) {
        List<Message> messages = readMessages(state);
        List<Message> updated = new ArrayList<>(messages.size() + 1);

        boolean merged = false;
        for (Message message : messages) {
            if (!merged && message instanceof SystemMessage systemMessage) {
                updated.add(new SystemMessage(systemMessage.getText() + "\n\n" + recallContext));
                merged = true;
            } else {
                updated.add(message);
            }
        }

        if (!merged) {
            updated.add(0, new SystemMessage(recallContext));
        }

        return updated;
    }

    @SuppressWarnings("unchecked")
    private List<Message> readMessages(OverAllState state) {
        return (List<Message>) state.value("messages").orElseGet(ArrayList::new);
    }

    private String buildRecallContext(List<LongTermMemoryManager.Memory> memories) {
        StringBuilder context = new StringBuilder();
        context.append("【相关历史对话】\n");
        for (LongTermMemoryManager.Memory memory : memories) {
            context.append("- ").append(memory.getContent()).append("\n");
        }
        context.append("【历史对话结束】");

        if (context.length() > maxContextLength) {
            return context.substring(0, maxContextLength) + "...(truncated)";
        }
        return context.toString();
    }
}
