package org.example.Hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import lombok.extern.slf4j.Slf4j;
import org.example.memory.markdown.AutoMemoryExtractor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Markdown 记忆持久化 Hook
 *
 * 在模型调用后触发自动记忆提取，将对话中的有价值信息保存为 Markdown 记忆文件。
 */
@Slf4j
@Component
public class MarkdownMemoryPersistHook extends ModelHook {

    @Autowired
    private AutoMemoryExtractor memoryExtractor;

    @Value("${memory.markdown.enabled:true}")
    private boolean enabled;

    @Value("${memory.markdown.hooks.persist.enabled:true}")
    private boolean persistEnabled;

    @Value("${memory.markdown.hooks.persist.min-messages:3}")
    private int minMessages;

    @Override
    public String getName() {
        return "markdown_memory_persist";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.AFTER_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        if (!enabled || !persistEnabled) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        try {
            String sessionId = resolveSessionId(config);
            if (sessionId == null || sessionId.isBlank()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }

            List<Message> messages = readMessages(state);
            if (messages.size() < minMessages) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }

            // 提取消息列表
            List<Map<String, String>> messageList = extractMessages(messages);
            if (messageList.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }

            // 异步提取记忆
            String userId = extractUserId(config);
            memoryExtractor.extractFromConversation(userId, sessionId, messageList);

            log.debug("MarkdownMemoryPersistHook triggered for session: {}", sessionId);
        } catch (Exception e) {
            log.warn("MarkdownMemoryPersistHook failed: {}", e.getMessage());
        }

        return CompletableFuture.completedFuture(Collections.emptyMap());
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

    private String extractUserId(RunnableConfig config) {
        return config.metadata("userId")
                .map(String::valueOf)
                .filter(id -> !id.isBlank())
                .orElse("default");
    }

    @SuppressWarnings("unchecked")
    private List<Message> readMessages(OverAllState state) {
        return (List<Message>) state.value("messages").orElseGet(ArrayList::new);
    }

    private List<Map<String, String>> extractMessages(List<Message> messages) {
        List<Map<String, String>> result = new ArrayList<>();

        for (Message message : messages) {
            Map<String, String> msg = new HashMap<>();
            if (message instanceof UserMessage userMessage) {
                msg.put("role", "user");
                msg.put("content", userMessage.getText());
            } else if (message instanceof AssistantMessage assistantMessage) {
                msg.put("role", "assistant");
                msg.put("content", assistantMessage.getText());
            } else {
                continue;  // 跳过其他类型的消息
            }
            result.add(msg);
        }

        return result;
    }
}
