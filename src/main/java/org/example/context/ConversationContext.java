package org.example.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 对话上下文
 *
 * 包含当前对话的所有上下文信息，用于上下文组装流水线。
 */
public class ConversationContext {

    private final String sessionId;
    private final String userId;
    private final String userQuery;
    private final List<Map<String, String>> recentMessages;
    private final Map<String, Object> metadata;
    private final Instant startTime;
    private final int messageCount;

    private ConversationContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.userQuery = builder.userQuery;
        this.recentMessages = builder.recentMessages;
        this.metadata = builder.metadata;
        this.startTime = builder.startTime;
        this.messageCount = builder.messageCount;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public List<Map<String, String>> getRecentMessages() {
        return recentMessages;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public int getMessageCount() {
        return messageCount;
    }

    /**
     * 获取指定元数据
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * 格式化最近消息为文本
     */
    public String formatRecentMessages() {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : recentMessages) {
            String role = msg.getOrDefault("role", "unknown");
            String content = msg.getOrDefault("content", "");
            sb.append(role).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String userId;
        private String userQuery;
        private List<Map<String, String>> recentMessages;
        private Map<String, Object> metadata;
        private Instant startTime = Instant.now();
        private int messageCount;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder userQuery(String userQuery) {
            this.userQuery = userQuery;
            return this;
        }

        public Builder recentMessages(List<Map<String, String>> recentMessages) {
            this.recentMessages = recentMessages;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder messageCount(int messageCount) {
            this.messageCount = messageCount;
            return this;
        }

        public ConversationContext build() {
            return new ConversationContext(this);
        }
    }
}
