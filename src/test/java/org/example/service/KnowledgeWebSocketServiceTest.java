package org.example.service;

import org.example.model.knowledge.MaintenanceTask;
import org.example.model.knowledge.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 测试：WebSocket 推送服务
 */
class KnowledgeWebSocketServiceTest {

    private KnowledgeWebSocketService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeWebSocketService();
    }

    @Test
    void addListener_shouldRegisterListener() {
        List<String> received = new ArrayList<>();
        service.addListener(msg -> received.add(msg));

        assertEquals(1, service.getListenerCount());
    }

    @Test
    void removeListener_shouldUnregisterListener() {
        List<String> received = new ArrayList<>();
        java.util.function.Consumer<String> listener = msg -> received.add(msg);
        service.addListener(listener);
        service.removeListener(listener);

        assertEquals(0, service.getListenerCount());
    }

    @Test
    void notifyTaskUpdate_shouldNotifyAllListeners() {
        List<String> received = new ArrayList<>();
        service.addListener(msg -> received.add(msg));

        MaintenanceTask task = buildTask(TaskStatus.EXTRACTING);
        service.notifyTaskUpdate(task);

        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("TASK_UPDATE"));
        assertTrue(received.get(0).contains(task.getTaskId()));
        assertTrue(received.get(0).contains("EXTRACTING"));
    }

    @Test
    void notifyReviewRequired_shouldSendReviewNotification() {
        List<String> received = new ArrayList<>();
        service.addListener(msg -> received.add(msg));

        MaintenanceTask task = buildTask(TaskStatus.PENDING_REVIEW);
        task.setGeneratedFilename("test.md");
        service.notifyReviewRequired(task);

        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("REVIEW_REQUIRED"));
        assertTrue(received.get(0).contains("test.md"));
    }

    @Test
    void notifyTaskCompleted_shouldSendCompletedNotification() {
        List<String> received = new ArrayList<>();
        service.addListener(msg -> received.add(msg));

        MaintenanceTask task = buildTask(TaskStatus.COMPLETED);
        task.setGeneratedFilename("output.md");
        service.notifyTaskCompleted(task);

        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("TASK_COMPLETED"));
        assertTrue(received.get(0).contains("COMPLETED"));
    }

    @Test
    void notifyTaskUpdate_shouldNotifyMultipleListeners() {
        List<String> received1 = new ArrayList<>();
        List<String> received2 = new ArrayList<>();
        service.addListener(msg -> received1.add(msg));
        service.addListener(msg -> received2.add(msg));

        MaintenanceTask task = buildTask(TaskStatus.GENERATING);
        service.notifyTaskUpdate(task);

        assertEquals(1, received1.size());
        assertEquals(1, received2.size());
    }

    @Test
    void notifyTaskUpdate_shouldHandleListenerException() {
        // 一个 listener 抛异常不应影响其他 listener
        List<String> received = new ArrayList<>();
        service.addListener(msg -> { throw new RuntimeException("test error"); });
        service.addListener(msg -> received.add(msg));

        MaintenanceTask task = buildTask(TaskStatus.INDEXING);
        assertDoesNotThrow(() -> service.notifyTaskUpdate(task));
        assertEquals(1, received.size(), "Second listener should still receive the message");
    }

    @Test
    void getListenerCount_shouldReturnCorrectCount() {
        assertEquals(0, service.getListenerCount());
        service.addListener(msg -> {});
        assertEquals(1, service.getListenerCount());
        service.addListener(msg -> {});
        assertEquals(2, service.getListenerCount());
    }

    // ==================== 辅助方法 ====================

    private MaintenanceTask buildTask(TaskStatus status) {
        return MaintenanceTask.builder()
                .taskId(java.util.UUID.randomUUID().toString())
                .status(status)
                .currentAgent("test_agent")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
