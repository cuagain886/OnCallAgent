package org.example.memory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 短期记忆管理器
 * 负责管理会话级别的短期记忆，支持持久化和自动清理
 */
@Slf4j
@Service
public class ShortTermMemoryManager {
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${memory.short-term.max-window-size:6}")
    private int maxWindowSize;

    @Value("${memory.short-term.session-ttl:3600}")
    private long sessionTTL; // 会话过期时间（秒）

    @Value("${memory.short-term.enable-redis:true}")
    private boolean enableRedis;

    private final Map<String, SessionMemory> inMemorySessions = new ConcurrentHashMap<>();
    /**
     * 添加对话到短期记忆
     */
    public void addMessage(String sessionId, String role, String content) {
        SessionMemory session = getOrCreateSession(sessionId);
        session.addMessage(role, content);

        if (enableRedis && redisTemplate != null) {
            saveToRedis(sessionId, session);
        }
    }

    /**
     * 获取会话历史
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        SessionMemory session = getSession(sessionId);
        if (session == null) {
            return Collections.emptyList();
        }
        return session.getHistory();
    }

    /**
     * 清空会话历史
     */
    public void clearHistory(String sessionId) {
        SessionMemory session = getSession(sessionId);
        if (session != null) {
            session.clearHistory();
            if (enableRedis && redisTemplate != null) {
                redisTemplate.delete(getRedisKey(sessionId));
            }
        }
    }

    /**
     * 获取或创建会话
     */
    private SessionMemory getOrCreateSession(String sessionId) {
        return inMemorySessions.computeIfAbsent(sessionId, id -> {
            SessionMemory session = new SessionMemory(id, maxWindowSize);

            // 尝试从 Redis 恢复
            if (enableRedis && redisTemplate != null) {
                SessionMemory cached = loadFromRedis(sessionId);
                if (cached != null) {
                    log.info("从 Redis 恢复会话: {}", sessionId);
                    return cached;
                }
            }

            return session;
        });
    }

    /**
     * 获取会话
     */
    private SessionMemory getSession(String sessionId) {
        return inMemorySessions.get(sessionId);
    }

    /**
     * 保存到 Redis
     */
    private void saveToRedis(String sessionId, SessionMemory session) {
        try {
            String key = getRedisKey(sessionId);
            redisTemplate.opsForValue().set(key, session, sessionTTL, TimeUnit.SECONDS);
            log.debug("会话已保存到 Redis: {}", sessionId);
        } catch (Exception e) {
            log.error("保存会话到 Redis 失败: {}", sessionId, e);
        }
    }

    /**
     * 从 Redis 加载
     */
    private SessionMemory loadFromRedis(String sessionId) {
        try {
            String key = getRedisKey(sessionId);
            return (SessionMemory) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("从 Redis 加载会话失败: {}", sessionId, e);
            return null;
        }
    }

    private String getRedisKey(String sessionId) {
        return "session:" + sessionId;
    }

    /**
     * 会话记忆数据类
     */
    @Data
    public static class SessionMemory {
        private final String sessionId;
        private final List<Map<String, String>> messageHistory;
        private final ReentrantLock lock;
        private final int maxWindowSize;
        private final long createTime;

        public SessionMemory(String sessionId, int maxWindowSize) {
            this.sessionId = sessionId;
            this.maxWindowSize = maxWindowSize;
            this.messageHistory = new ArrayList<>();
            this.lock = new ReentrantLock();
            this.createTime = System.currentTimeMillis();
        }

        public void addMessage(String role, String content) {
            lock.lock();
            try {
                messageHistory.add(Map.of("role", role, "content", content));

                // 自动管理历史消息窗口大小
                while (messageHistory.size() > maxWindowSize * 2) {
                    messageHistory.remove(0);
                    messageHistory.remove(0);
                }
            } finally {
                lock.unlock();
            }
        }

        public List<Map<String, String>> getHistory() {
            lock.lock();
            try {
                return new ArrayList<>(messageHistory);
            } finally {
                lock.unlock();
            }
        }

        public void clearHistory() {
            lock.lock();
            try {
                messageHistory.clear();
            } finally {
                lock.unlock();
            }
        }

        public int getMessagePairCount() {
            return messageHistory.size() / 2;
        }
    }
}
