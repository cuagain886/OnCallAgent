package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Markdown 记忆系统配置
 *
 * 配置项以 memory.markdown 开头
 */
@Configuration
@ConfigurationProperties(prefix = "memory.markdown")
public class MarkdownMemoryConfig {

    /**
     * 是否启用 Markdown 记忆系统
     */
    private boolean enabled = true;

    /**
     * 记忆文件存储根目录
     * 默认：~/.agent-memory/
     */
    private String root = System.getProperty("user.home") + "/.agent-memory";

    /**
     * 记忆召回配置
     */
    private Recall recall = new Recall();

    /**
     * 记忆持久化配置
     */
    private Persist persist = new Persist();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public Recall getRecall() {
        return recall;
    }

    public void setRecall(Recall recall) {
        this.recall = recall;
    }

    public Persist getPersist() {
        return persist;
    }

    public void setPersist(Persist persist) {
        this.persist = persist;
    }

    /**
     * 记忆召回配置
     */
    public static class Recall {
        /**
         * 是否启用记忆召回 Hook
         */
        private boolean enabled = true;

        /**
         * 召回的最大上下文长度
         */
        private int maxContextLength = 3000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxContextLength() {
            return maxContextLength;
        }

        public void setMaxContextLength(int maxContextLength) {
            this.maxContextLength = maxContextLength;
        }
    }

    /**
     * 记忆持久化配置
     */
    public static class Persist {
        /**
         * 是否启用记忆持久化 Hook
         */
        private boolean enabled = true;

        /**
         * 触发持久化的最小消息数
         */
        private int minMessages = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinMessages() {
            return minMessages;
        }

        public void setMinMessages(int minMessages) {
            this.minMessages = minMessages;
        }
    }
}
