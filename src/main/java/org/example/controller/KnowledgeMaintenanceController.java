package org.example.controller;

import org.example.model.knowledge.MaintenanceTask;
import org.example.model.knowledge.TaskStatus;
import org.example.service.KnowledgeMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * 知识库自维护 API
 * Phase 3: 新增统计接口、优化响应结构
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeMaintenanceController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeMaintenanceController.class);

    @Autowired
    private KnowledgeMaintenanceService maintenanceService;

    @Value("${file.upload.path}")
    private String knowledgeBasePath;

    /**
     * 手动触发知识库维护任务
     */
    @PostMapping("/maintain")
    public ResponseEntity<Map<String, Object>> triggerMaintenance(@RequestBody Map<String, String> request) {
        String reportContent = request.get("reportContent");
        if (reportContent == null || reportContent.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "reportContent 不能为空"));
        }

        boolean autoApprove = "true".equals(request.get("autoApprove"));
        String taskId = maintenanceService.submitReport(reportContent, autoApprove);
        logger.info("知识库维护任务已提交: {}, autoApprove={}", taskId, autoApprove);
        return ResponseEntity.ok(Map.of("taskId", taskId, "status", "PENDING", "autoApprove", autoApprove));
    }

    /**
     * 查询维护任务状态
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        MaintenanceTask task = maintenanceService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(taskToMap(task));
    }

    /**
     * 列出所有维护任务
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> listTasks(
            @RequestParam(required = false) String status) {
        List<MaintenanceTask> tasks = maintenanceService.listTasks();

        if (status != null && !status.isBlank()) {
            TaskStatus filterStatus = TaskStatus.valueOf(status.toUpperCase());
            tasks = tasks.stream()
                    .filter(t -> t.getStatus() == filterStatus)
                    .toList();
        }

        List<Map<String, Object>> result = tasks.stream()
                .map(this::taskToMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 审核任务（人工确认/拒绝）
     */
    @PostMapping("/review/{taskId}")
    public ResponseEntity<Map<String, Object>> reviewTask(
            @PathVariable String taskId,
            @RequestBody Map<String, String> request) {
        String action = request.get("action");
        String feedback = request.getOrDefault("feedback", "");

        if (action == null || (!action.equals("approve") && !action.equals("reject"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "action 必须为 approve 或 reject"));
        }

        boolean approved = action.equals("approve");
        boolean success = maintenanceService.reviewTask(taskId, approved, feedback);

        if (!success) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "审核失败：任务不存在或状态不正确",
                    "taskId", taskId
            ));
        }

        logger.info("任务审核完成: taskId={}, action={}, feedback={}", taskId, action, feedback);
        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "action", action,
                "status", approved ? "APPROVED" : "REJECTED"
        ));
    }

    /**
     * 获取待审核任务列表
     */
    @GetMapping("/pending-review")
    public ResponseEntity<List<Map<String, Object>>> getPendingReviewTasks() {
        List<Map<String, Object>> result = maintenanceService.getPendingReviewTasks().stream()
                .map(this::taskToMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Phase 3: 知识库统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 文档统计
        Path dir = Paths.get(knowledgeBasePath);
        int totalDocs = 0;
        int mdDocs = 0;
        int txtDocs = 0;
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                List<Path> allFiles = files.filter(Files::isRegularFile).toList();
                totalDocs = allFiles.size();
                mdDocs = (int) allFiles.stream().filter(p -> p.toString().endsWith(".md")).count();
                txtDocs = (int) allFiles.stream().filter(p -> p.toString().endsWith(".txt")).count();
            } catch (IOException e) {
                logger.warn("统计文档数量失败", e);
            }
        }

        // 任务统计
        List<MaintenanceTask> allTasks = maintenanceService.listTasks();
        long completedTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long failedTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count();
        long skippedTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.SKIPPED).count();
        long pendingReviewTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING_REVIEW).count();
        long inProgressTasks = allTasks.stream().filter(t ->
                t.getStatus() == TaskStatus.EXTRACTING ||
                t.getStatus() == TaskStatus.CLASSIFYING ||
                t.getStatus() == TaskStatus.GENERATING ||
                t.getStatus() == TaskStatus.INDEXING
        ).count();

        stats.put("documents", Map.of(
                "total", totalDocs,
                "markdown", mdDocs,
                "text", txtDocs,
                "knowledgeBasePath", knowledgeBasePath
        ));
        stats.put("tasks", Map.of(
                "total", allTasks.size(),
                "completed", completedTasks,
                "failed", failedTasks,
                "skipped", skippedTasks,
                "pendingReview", pendingReviewTasks,
                "inProgress", inProgressTasks
        ));

        return ResponseEntity.ok(stats);
    }

    /**
     * Phase 3: 列出知识库文档
     */
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, String>>> listDocuments() {
        List<Map<String, String>> docs = new ArrayList<>();
        Path dir = Paths.get(knowledgeBasePath);

        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".txt"))
                        .sorted()
                        .forEach(file -> {
                            String filename = dir.relativize(file).toString().replace("\\", "/");
                            String title = extractTitle(file);
                            long size = 0;
                            try {
                                size = Files.size(file);
                            } catch (IOException ignored) {}
                            docs.add(Map.of(
                                    "filename", filename,
                                    "title", title != null ? title : filename,
                                    "size", String.valueOf(size)
                            ));
                        });
            } catch (IOException e) {
                logger.warn("列出文档失败", e);
            }
        }

        return ResponseEntity.ok(docs);
    }

    private Map<String, Object> taskToMap(MaintenanceTask task) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("taskId", task.getTaskId());
        map.put("status", task.getStatus().name());
        map.put("currentAgent", task.getCurrentAgent() != null ? task.getCurrentAgent() : "");
        map.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : "");
        map.put("completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : "");
        map.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "");
        map.put("autoApprove", task.isAutoApprove());
        map.put("generatedFilename", task.getGeneratedFilename() != null ? task.getGeneratedFilename() : "");
        map.put("reviewerFeedback", task.getReviewerFeedback() != null ? task.getReviewerFeedback() : "");
        String content = task.getGeneratedContent();
        if (content != null && content.length() > 500) {
            map.put("generatedContentPreview", content.substring(0, 500) + "...");
        } else {
            map.put("generatedContentPreview", content != null ? content : "");
        }
        return map;
    }

    private String extractTitle(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return trimmed.substring(2).trim();
                }
            }
        } catch (IOException ignored) {}
        return null;
    }
}
