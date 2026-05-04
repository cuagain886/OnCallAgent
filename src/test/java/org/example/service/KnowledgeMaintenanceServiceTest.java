package org.example.service;

import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.knowledge.*;
import org.example.config.KnowledgeMaintenanceConfig;
import org.example.model.knowledge.MaintenanceTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知识库自维护服务测试
 * 验证任务管理、串行化队列、报告存储等核心逻辑
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeMaintenanceServiceTest {

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

    // ==================== 任务提交测试 ====================

    @Test
    void submitReport_shouldCreateTask() {
        String taskId = service.submitReport("# 测试报告\n\nCPU 高负载告警分析...");

        assertNotNull(taskId);
        MaintenanceTask task = service.getTask(taskId);
        assertNotNull(task);
        // 任务提交后会立即开始处理（异步），状态可能已变为 EXTRACTING 或更高
        assertNotNull(task.getStatus(), "Task should have a status");
        assertEquals("MANUAL", task.getTriggerType());
        assertNotNull(task.getCreatedAt());
    }

    @Test
    void submitReport_shouldSaveReportFile() throws IOException {
        String reportContent = "# 测试报告\n\nCPU 高负载告警分析";
        String taskId = service.submitReport(reportContent);

        // 验证报告文件已保存
        Path reportFile = tempDir.resolve(taskId + ".md");
        assertTrue(Files.exists(reportFile), "Report file should be saved");
        assertEquals(reportContent, Files.readString(reportFile));
    }

    @Test
    void submitReport_shouldReturnUniqueTaskIds() {
        String taskId1 = service.submitReport("报告1");
        String taskId2 = service.submitReport("报告2");

        assertNotEquals(taskId1, taskId2, "Task IDs should be unique");
    }

    // ==================== 任务查询测试 ====================

    @Test
    void getTask_shouldReturnNullForUnknownId() {
        MaintenanceTask task = service.getTask("non-existent-id");
        assertNull(task);
    }

    @Test
    void listTasks_shouldReturnAllTasks() {
        service.submitReport("报告1");
        service.submitReport("报告2");
        service.submitReport("报告3");

        List<MaintenanceTask> tasks = service.listTasks();
        assertEquals(3, tasks.size());
    }

    @Test
    void listTasks_shouldReturnEmptyWhenNoTasks() {
        List<MaintenanceTask> tasks = service.listTasks();
        assertTrue(tasks.isEmpty());
    }

    // ==================== 配置测试 ====================

    @Test
    void config_shouldHaveCorrectDefaults() {
        KnowledgeMaintenanceConfig defaultConfig = new KnowledgeMaintenanceConfig();
        assertTrue(defaultConfig.isEnabled());
        assertTrue(defaultConfig.isAutoTrigger());
        assertEquals(1, defaultConfig.getMaxRetry());
        assertEquals(0.7, defaultConfig.getQualityThreshold());
        assertEquals(0.5, defaultConfig.getConfidenceThreshold());
        assertEquals("aiops-reports", defaultConfig.getReportStoragePath());
        assertEquals(30, defaultConfig.getRetentionDays());
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
