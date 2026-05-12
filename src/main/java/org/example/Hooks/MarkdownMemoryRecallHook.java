package org.example.Hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import lombok.extern.slf4j.Slf4j;
import org.example.memory.markdown.MarkdownMemoryRecallManager;
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
import java.util.concurrent.CompletableFuture;

/**
 * Markdown 记忆召回 Hook
 *
 * 在模型调用前从 Markdown 记忆文件中检索相关内容，并注入到系统上下文中。
 * 使用异步 Prefetch + LLM 智能筛选机制。
 */
@Slf4j
@Component
public class MarkdownMemoryRecallHook extends ModelHook {

    @Autowired
    private MarkdownMemoryRecallManager recallManager;

    @Value("${memory.markdown.enabled:true}")
    private boolean enabled;

    @Value("${memory.markdown.hooks.recall.enabled:true}")
    private boolean recallEnabled;

    @Override
    public String getName() {
        return "markdown_memory_recall";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.BEFORE_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        if (!enabled || !recallEnabled) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        try {
            String question = extractLatestUserQuestion(state);
            if (question == null || question.isBlank()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }

            // 异步召回记忆
            return recallManager.recallAsync(question)
                    .thenApply(memories -> {
                        if (memories.isEmpty()) {
                            log.debug("No markdown memories recalled");
                            return Collections.<String, Object>emptyMap();
                        }

                        String recallContext = recallManager.buildRecallContext(memories);
                        List<Message> updatedMessages = mergeIntoSystemMessage(state, recallContext);

                        log.info("MarkdownMemoryRecallHook injected {} memories", memories.size());
                        Map<String, Object> result = new java.util.HashMap<>();
                        result.put("messages", ReplaceAllWith.of(updatedMessages));
                        return result;
                    })
                    .exceptionally(e -> {
                        log.warn("MarkdownMemoryRecallHook failed, degrading gracefully: {}", e.getMessage());
                        return Collections.emptyMap();
                    });
        } catch (Exception e) {
            log.warn("MarkdownMemoryRecallHook failed, degrading gracefully: {}", e.getMessage());
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
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
}
