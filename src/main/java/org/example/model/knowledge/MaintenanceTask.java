package org.example.model.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceTask {
    private String taskId;
    private String triggerType;     // AIOPS_REPORT | MANUAL
    private String triggerId;
    private TaskStatus status;
    private String currentAgent;
    private ExtractorResult extractorResult;
    private ClassifierResult classifierResult;
    private GeneratorResult generatorResult;
    private IndexerResult indexerResult;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private int retryCount;
    // Phase 2: 人工审核字段
    private boolean autoApprove;        // 是否自动通过（无需人工审核）
    private String reviewerFeedback;    // 审核者反馈
    private String generatedContent;    // 生成的文档内容（供审核查看）
    private String generatedFilename;   // 生成的文件名
    private LocalDateTime reviewedAt;
}
