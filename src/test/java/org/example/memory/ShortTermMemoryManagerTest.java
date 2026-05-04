package org.example.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H3 修复验证：SessionMemory 可序列化 + 外部锁 + 滑动窗口
 */
class ShortTermMemoryManagerTest {

    private ShortTermMemoryManager manager;

    @BeforeEach
    void setUp() {
        manager = new ShortTermMemoryManager();
        // 通过反射设置私有字段（不依赖 Spring 上下文）
        setField(manager, "maxWindowSize", 3);
        setField(manager, "sessionTTL", 3600L);
        setField(manager, "enableRedis", false);  // 纯内存模式，不依赖 Redis
    }

    // ==================== H3: SessionMemory 可序列化测试 ====================

    @Test
    void sessionMemory_shouldBeSerializable() throws Exception {
        // SessionMemory 实现了 Serializable，不再包含 ReentrantLock
        ShortTermMemoryManager.SessionMemory session =
                new ShortTermMemoryManager.SessionMemory("test-session", 3);
        session.addMessage("user", "你好");
        session.addMessage("assistant", "你好！有什么可以帮助你的吗？");

        // 序列化
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(session);
        oos.flush();
        byte[] serialized = bos.toByteArray();

        // 反序列化
        ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
        ObjectInputStream ois = new ObjectInputStream(bis);
        ShortTermMemoryManager.SessionMemory deserialized =
                (ShortTermMemoryManager.SessionMemory) ois.readObject();

        // 验证
        assertEquals("test-session", deserialized.getSessionId());
        assertEquals(2, deserialized.getMessageHistory().size());
        assertEquals("user", deserialized.getMessageHistory().get(0).get("role"));
        assertEquals("你好", deserialized.getMessageHistory().get(0).get("content"));
        assertEquals("assistant", deserialized.getMessageHistory().get(1).get("role"));
        assertEquals(3, deserialized.getMaxWindowSize());
    }

    @Test
    void sessionMemory_shouldNotContainReentrantLock() {
        // 验证 SessionMemory 的字段中没有 ReentrantLock
        ShortTermMemoryManager.SessionMemory session =
                new ShortTermMemoryManager.SessionMemory("test", 3);

        boolean hasLockField = false;
        for (var field : session.getClass().getDeclaredFields()) {
            if (field.getType() == java.util.concurrent.locks.ReentrantLock.class) {
                hasLockField = true;
                break;
            }
        }
        assertFalse(hasLockField, "SessionMemory should not contain ReentrantLock field");
    }

    // ==================== 滑动窗口测试 ====================

    @Test
    void addMessage_shouldEnforceSlidingWindow() {
        // maxWindowSize = 3，最多保留 6 条消息（3 轮）
        manager.addMessage("session-1", "user", "Q1");
        manager.addMessage("session-1", "assistant", "A1");
        manager.addMessage("session-1", "user", "Q2");
        manager.addMessage("session-1", "assistant", "A2");
        manager.addMessage("session-1", "user", "Q3");
        manager.addMessage("session-1", "assistant", "A3");

        List<Map<String, String>> history = manager.getHistory("session-1");
        assertEquals(6, history.size());

        // 添加第 4 轮，触发淘汰（移除 Q1, A1）
        manager.addMessage("session-1", "user", "Q4");
        manager.addMessage("session-1", "assistant", "A4");

        history = manager.getHistory("session-1");
        assertEquals(6, history.size());
        // 最旧的应该是 Q2（Q1, A1 已被淘汰）
        assertEquals("Q2", history.get(0).get("content"));
        assertEquals("A2", history.get(1).get("content"));
        // 最新的应该是 Q4, A4
        assertEquals("Q4", history.get(4).get("content"));
        assertEquals("A4", history.get(5).get("content"));
    }

    @Test
    void addMessage_shouldMaintainPairAlignment() {
        // 验证淘汰后消息仍然成对出现
        manager.addMessage("s1", "user", "Q1");
        manager.addMessage("s1", "assistant", "A1");
        manager.addMessage("s1", "user", "Q2");
        manager.addMessage("s1", "assistant", "A2");
        manager.addMessage("s1", "user", "Q3");
        manager.addMessage("s1", "assistant", "A3");
        manager.addMessage("s1", "user", "Q4");  // 触发淘汰

        List<Map<String, String>> history = manager.getHistory("s1");
        // 7 条消息，淘汰 2 条后剩 5 条，再加 assistant 后应为 6
        // 但这里只加了 Q4 没加 A4，所以是 5 条
        // 滑动窗口只在 size > maxWindowSize*2 时淘汰
        // 7 > 6 → 淘汰 2 条 → 5 条
        assertEquals(5, history.size());

        // 验证剩余消息的 role 顺序
        assertEquals("user", history.get(0).get("role"));
        assertEquals("assistant", history.get(1).get("role"));
        assertEquals("user", history.get(2).get("role"));
        assertEquals("assistant", history.get(3).get("role"));
        assertEquals("user", history.get(4).get("role"));
    }

    // ==================== 外部锁并发安全测试 ====================

    @Test
    void addMessage_shouldBeThreadSafe() throws Exception {
        // 验证外部锁保证并发写入不会丢失消息
        int threadCount = 10;
        int messagesPerThread = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicBoolean hasError = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();  // 所有线程同时开始
                    for (int i = 0; i < messagesPerThread; i++) {
                        manager.addMessage("concurrent-session", "user",
                                "thread-" + threadId + "-msg-" + i);
                    }
                } catch (Exception e) {
                    hasError.set(true);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 启动所有线程
        doneLatch.await();       // 等待所有线程完成

        assertFalse(hasError.get(), "Concurrent addMessage should not throw");

        // 总消息数 = threadCount * messagesPerThread = 500
        // 但滑动窗口 maxWindowSize=3，最多保留 6 条
        List<Map<String, String>> history = manager.getHistory("concurrent-session");
        assertTrue(history.size() <= 6, "History should respect sliding window");
        assertTrue(history.size() > 0, "History should not be empty");

        executor.shutdown();
    }

    // ==================== 基本操作测试 ====================

    @Test
    void getHistory_shouldReturnEmptyForUnknownSession() {
        List<Map<String, String>> history = manager.getHistory("non-existent");
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void getHistory_shouldReturnDefensiveCopy() {
        manager.addMessage("s1", "user", "Q1");
        List<Map<String, String>> history1 = manager.getHistory("s1");
        List<Map<String, String>> history2 = manager.getHistory("s1");

        // 两次获取应该是不同的对象（防御性拷贝）
        assertNotSame(history1, history2);
        assertEquals(history1, history2);
    }

    @Test
    void clearHistory_shouldRemoveAllMessages() {
        manager.addMessage("s1", "user", "Q1");
        manager.addMessage("s1", "assistant", "A1");
        assertEquals(2, manager.getHistory("s1").size());

        manager.clearHistory("s1");
        assertTrue(manager.getHistory("s1").isEmpty());
    }

    @Test
    void clearHistory_shouldNotAffectOtherSessions() {
        manager.addMessage("s1", "user", "Q1");
        manager.addMessage("s2", "user", "Q2");

        manager.clearHistory("s1");

        assertTrue(manager.getHistory("s1").isEmpty());
        assertEquals(1, manager.getHistory("s2").size());
    }

    // ==================== 辅助方法 ====================

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
