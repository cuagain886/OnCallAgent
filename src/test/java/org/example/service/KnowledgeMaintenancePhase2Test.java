package org.example.service;

import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.knowledge.*;
import org.example.config.KnowledgeMaintenanceConfig;
import org.example.model.knowledge.MaintenanceTask;
import org.example.model.knowledge.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 测试：UPDATE 流程 + 人工审核
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeMaintenancePhase2Test {

    @Mock
    private ChatService chatService;
    @Mock
    private DocSearchTool docSearchTool;
    @Mock
    private DocListTool docListTool;
    @Mock
    private DocWriteTool docWriteTool;
    @Mock
    private DocIndexTool docIndexTool;
    @Mock
    private QualityTool qualityTool;
    @Mock
    private TemplateTool templateTool;
    @Mock
    private DateTimeTools dateTimeTools;

    private KnowledgeMaintenanceService service;
    private KnowledgeMaintenanceConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        config = new KnowledgeMaintenanceConfig();
        config.setReportStoragePath(tempDir.toString());
        config.setEnabled(true);
        config.setConfidenceThreshold(0.5);
        config.setMaxRetry(1);

        service = new KnowledgeMaintenanceService();
        setField(service, "config", config);
        setField(service, "chatService", chatService);
        setField(service, "docSearchTool", docSearchTool);
        setField(service, "docListTool", docListTool);
        setField(service, "docWriteTool", docWriteTool);
        setField(service, "docIndexTool", docIndexTool);
        setField(service, "qualityTool", qualityTool);
        setField(service, "templateTool", templateTool);
        setField(service, "dateTimeTools", dateTimeTools);
    }

    // ==================== autoApprove 测试 ====================

    @Test
    void submitReport_shouldSupportAutoApproveFlag() {
        String taskId = service.submitReport("报告内容", true);
        MaintenanceTask task = service.getTask(taskId);

        assertNotNull(task);
        assertTrue(task.isAutoApprove(), "autoApprove should be true");
    }

    @Test
    void submitReport_shouldSupportManualReviewFlag() {
        String taskId = service.submitReport("报告内容", false);
        MaintenanceTask task = service.getTask(taskId);

        assertNotNull(task);
        assertFalse(task.isAutoApprove(), "autoApprove should be false");
    }

    @Test
    void submitReport_defaultShouldBeManualReview() {
        String taskId = service.submitReport("报告内容");
        MaintenanceTask task = service.getTask(taskId);

        assertNotNull(task);
        assertFalse(task.isAutoApprove(), "Default should be manual review");
    }

    // ==================== 人工审核测试 ====================

    @Test
    void reviewTask_shouldApproveTask() {
        // 创建一个处于 PENDING_REVIEW 状态的任务
        String taskId = createTaskInReviewState();

        boolean result = service.reviewTask(taskId, true, "文档质量良好");

        assertTrue(result, "Review should succeed");
        MaintenanceTask task = service.getTask(taskId);
        assertEquals(TaskStatus.APPROVED, task.getStatus());
        assertEquals("文档质量良好", task.getReviewerFeedback());
        assertNotNull(task.getReviewedAt());
    }

    @Test
    void reviewTask_shouldRejectTask() {
        String taskId = createTaskInReviewState();

        boolean result = service.reviewTask(taskId, false, "需要补充排查步骤");

        assertTrue(result, "Review should succeed");
        MaintenanceTask task = service.getTask(taskId);
        assertEquals(TaskStatus.REJECTED, task.getStatus());
        assertEquals("需要补充排查步骤", task.getReviewerFeedback());
        assertNotNull(task.getReviewedAt());
        assertNotNull(task.getCompletedAt(), "Rejected task should have completedAt");
    }

    @Test
    void reviewTask_shouldFailForNonExistentTask() {
        boolean result = service.reviewTask("non-existent-id", true, "ok");

        assertFalse(result, "Review should fail for non-existent task");
    }

    @Test
    void reviewTask_shouldFailForNonReviewTask() {
        // 创建一个 PENDING 状态的任务（不是 PENDING_REVIEW）
        String taskId = service.submitReport("报告内容");
        // 任务可能已开始处理，但不会是 PENDING_REVIEW（因为 Agent 调用会失败）
        // 直接测试非 PENDING_REVIEW 状态
        MaintenanceTask task = service.getTask(taskId);
        if (task.getStatus() != TaskStatus.PENDING_REVIEW) {
            boolean result = service.reviewTask(taskId, true, "ok");
            assertFalse(result, "Review should fail for non-review task");
        }
    }

    // ==================== 待审核列表测试 ====================

    @Test
    void getPendingReviewTasks_shouldReturnReviewTasks() {
        // 创建一个待审核任务
        createTaskInReviewState();

        List<MaintenanceTask> pendingTasks = service.getPendingReviewTasks();

        assertTrue(pendingTasks.size() >= 1, "Should have at least 1 pending review task");
        for (MaintenanceTask task : pendingTasks) {
            assertEquals(TaskStatus.PENDING_REVIEW, task.getStatus());
        }
    }

    @Test
    void getPendingReviewTasks_shouldNotIncludeApprovedTasks() {
        String taskId = createTaskInReviewState();
        service.reviewTask(taskId, true, "approved");

        List<MaintenanceTask> pendingTasks = service.getPendingReviewTasks();

        boolean foundApproved = pendingTasks.stream()
                .anyMatch(t -> t.getTaskId().equals(taskId));
        assertFalse(foundApproved, "Approved task should not be in pending list");
    }

    @Test
    void getPendingReviewTasks_shouldNotIncludeRejectedTasks() {
        String taskId = createTaskInReviewState();
        service.reviewTask(taskId, false, "rejected");

        List<MaintenanceTask> pendingTasks = service.getPendingReviewTasks();

        boolean foundRejected = pendingTasks.stream()
                .anyMatch(t -> t.getTaskId().equals(taskId));
        assertFalse(foundRejected, "Rejected task should not be in pending list");
    }

    // ==================== 任务状态转换测试 ====================

    @Test
    void task_shouldTrackGeneratedContent() {
        String taskId = createTaskInReviewState();
        MaintenanceTask task = service.getTask(taskId);

        assertNotNull(task.getGeneratedContent(), "Task should have generated content");
        assertNotNull(task.getGeneratedFilename(), "Task should have generated filename");
    }

    @Test
    void task_shouldSupportMultipleReviews() {
        // 创建两个待审核任务
        String taskId1 = createTaskInReviewState();
        String taskId2 = createTaskInReviewState();

        service.reviewTask(taskId1, true, "approved");
        service.reviewTask(taskId2, false, "rejected");

        assertEquals(TaskStatus.APPROVED, service.getTask(taskId1).getStatus());
        assertEquals(TaskStatus.REJECTED, service.getTask(taskId2).getStatus());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建一个处于 PENDING_REVIEW 状态的任务（绕过 Agent 调用）
     */
    private String createTaskInReviewState() {
        // 直接构造一个 PENDING_REVIEW 状态的任务
        String taskId = java.util.UUID.randomUUID().toString();
        MaintenanceTask task = MaintenanceTask.builder()
                .taskId(taskId)
                .triggerType("MANUAL")
                .triggerId(taskId)
                .status(TaskStatus.PENDING_REVIEW)
                .autoApprove(false)
                .createdAt(java.time.LocalDateTime.now())
                .generatedContent("# 测试文档\n\n## 告警名称\n测试告警\n\n## 问题描述\n测试问题\n\n## 排查步骤\n1. 检查日志\n\n## 常见根因\n### 根因 1\n测试根因\n\n## 应急处理\n### 立即执行\n重启服务\n\n## 验证步骤\n检查服务状态")
                .generatedFilename("test_scenario.md")
                .build();

        // 通过反射将任务放入 tasks map
        try {
            var field = KnowledgeMaintenanceService.class.getDeclaredField("tasks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.ConcurrentHashMap<String, MaintenanceTask> tasks =
                    (java.util.concurrent.ConcurrentHashMap<String, MaintenanceTask>) field.get(service);
            tasks.put(taskId, task);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject task", e);
        }

        return taskId;
    }

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
