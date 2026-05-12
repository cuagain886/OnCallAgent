package org.example.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.knowledge.*;
import org.example.config.KnowledgeMaintenanceConfig;
import org.example.model.knowledge.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * 知识库自维护服务
 * 编排 Extractor → Classifier → Generator → Indexer 四个 Agent 的工作流
 * Phase 2: 支持 UPDATE 流程和人工审核
 */
@Service
public class KnowledgeMaintenanceService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeMaintenanceService.class);

    @Autowired
    private KnowledgeMaintenanceConfig config;

    @Autowired
    private ChatService chatService;

    @Autowired
    private DocSearchTool docSearchTool;

    @Autowired
    private DocListTool docListTool;

    @Autowired
    private DocWriteTool docWriteTool;

    @Autowired
    private DocIndexTool docIndexTool;

    @Autowired
    private QualityTool qualityTool;

    @Autowired
    private TemplateTool templateTool;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired(required = false)
    private KnowledgeWebSocketService webSocketService;

    // 串行化队列：同一时间只处理一个任务
    private final Semaphore processingLock = new Semaphore(1);
    private final Queue<String> pendingReportIds = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, MaintenanceTask> tasks = new ConcurrentHashMap<>();

    /**
     * 提交 AIOps 报告进行知识库维护
     */
    public String submitReport(String reportContent) {
        return submitReport(reportContent, false);
    }

    /**
     * 提交 AIOps 报告进行知识库维护
     * @param autoApprove true=自动通过，false=需要人工审核
     */
    public String submitReport(String reportContent, boolean autoApprove) {
        String taskId = UUID.randomUUID().toString();
        MaintenanceTask task = MaintenanceTask.builder()
                .taskId(taskId)
                .triggerType("MANUAL")
                .triggerId(taskId)
                .status(TaskStatus.PENDING)
                .autoApprove(autoApprove)
                .createdAt(LocalDateTime.now())
                .build();
        tasks.put(taskId, task);

        // 将报告内容写入临时文件
        try {
            Path reportDir = Paths.get(config.getReportStoragePath());
            if (!Files.exists(reportDir)) {
                Files.createDirectories(reportDir);
            }
            Path reportFile = reportDir.resolve(taskId + ".md");
            Files.writeString(reportFile, reportContent);
        } catch (IOException e) {
            logger.error("保存报告文件失败", e);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("保存报告文件失败: " + e.getMessage());
            return taskId;
        }

        pendingReportIds.offer(taskId);
        processNextIfNeeded();
        return taskId;
    }

    /**
     * 查询任务状态
     */
    public MaintenanceTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 列出所有任务
     */
    public List<MaintenanceTask> listTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Phase 2: 审核任务（人工确认/拒绝）
     */
    public boolean reviewTask(String taskId, boolean approved, String feedback) {
        MaintenanceTask task = tasks.get(taskId);
        if (task == null) {
            logger.warn("审核失败：任务不存在: {}", taskId);
            return false;
        }
        if (task.getStatus() != TaskStatus.PENDING_REVIEW) {
            logger.warn("审核失败：任务状态不是 PENDING_REVIEW: {}", task.getStatus());
            return false;
        }

        task.setReviewerFeedback(feedback);
        task.setReviewedAt(LocalDateTime.now());

        if (approved) {
            task.setStatus(TaskStatus.APPROVED);
            notifyTaskUpdate(task);
            logger.info("[{}] 任务已批准，开始入库", taskId);
            // 异步执行入库
            CompletableFuture.runAsync(() -> executeIndexing(task));
        } else {
            task.setStatus(TaskStatus.REJECTED);
            task.setCompletedAt(LocalDateTime.now());
            notifyTaskUpdate(task);
            logger.info("[{}] 任务已拒绝: {}", taskId, feedback);
        }
        return true;
    }

    /**
     * 获取待审核的任务列表
     */
    public List<MaintenanceTask> getPendingReviewTasks() {
        return tasks.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING_REVIEW)
                .toList();
    }

    private void processNextIfNeeded() {
        if (!processingLock.tryAcquire()) return;

        String reportId = pendingReportIds.poll();
        if (reportId == null) {
            processingLock.release();
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                doProcessReport(reportId);
            } catch (Exception e) {
                logger.error("处理报告失败: {}", reportId, e);
                MaintenanceTask task = tasks.get(reportId);
                if (task != null) {
                    task.setStatus(TaskStatus.FAILED);
                    task.setErrorMessage(e.getMessage());
                    task.setCompletedAt(LocalDateTime.now());
                }
            } finally {
                processingLock.release();
                processNextIfNeeded();
            }
        });
    }

    private void doProcessReport(String reportId) throws GraphRunnerException {
        MaintenanceTask task = tasks.get(reportId);
        if (task == null) return;

        // 读取报告内容
        String reportContent = readReportContent(reportId);
        if (reportContent == null || reportContent.isBlank()) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("报告内容为空");
            task.setCompletedAt(LocalDateTime.now());
            return;
        }

        // 构建 Agent（maxTokens=8192 确保完整文档不会被截断）
        OpenAiApi openAiApi = chatService.createOpenAiApi();
        ChatModel agentModel = chatService.createChatModel(openAiApi, 0.3, 8192, 0.9, chatService.getAiOpsModelName());

        // 构建工具集
        Object[] tools = {docSearchTool, docListTool, docWriteTool, docIndexTool, qualityTool, templateTool, dateTimeTools};

        // 阶段 1：Extractor
        task.setStatus(TaskStatus.EXTRACTING);
        task.setCurrentAgent("extractor");
        logger.info("[{}] 开始信息提取", reportId);

        ReactAgent extractor = ReactAgent.builder()
                .name("extractor_agent")
                .model(agentModel)
                .systemPrompt(buildExtractorPrompt())
                .methodTools(tools)
                .outputKey("extractor_result")
                .build();

        OverAllState extractorState = extractor.invoke("请从以下 AIOps 报告中提取结构化信息：\n\n" + reportContent)
                .orElseThrow(() -> new GraphRunnerException("Extractor 未返回结果"));

        String extractorOutput = extractOutput(extractorState, "extractor_result");
        logger.info("[{}] 信息提取完成: {}", reportId, truncate(extractorOutput, 100));

        // 阶段 2：Classifier（Phase 2: 增强的相似度检测）
        task.setStatus(TaskStatus.CLASSIFYING);
        task.setCurrentAgent("classifier");
        logger.info("[{}] 开始场景分类", reportId);

        ReactAgent classifier = ReactAgent.builder()
                .name("classifier_agent")
                .model(agentModel)
                .systemPrompt(buildClassifierPrompt())
                .methodTools(tools)
                .outputKey("classifier_result")
                .build();

        OverAllState classifierState = classifier.invoke(
                "请根据以下提取结果判断应如何处理：\n\n" + extractorOutput)
                .orElseThrow(() -> new GraphRunnerException("Classifier 未返回结果"));

        String classifierOutput = extractOutput(classifierState, "classifier_result");
        logger.info("[{}] 场景分类完成: {}", reportId, truncate(classifierOutput, 100));

        // 检查是否 SKIP
        if (classifierOutput.contains("\"SKIP\"") || classifierOutput.contains("SKIP")) {
            task.setStatus(TaskStatus.SKIPPED);
            task.setCompletedAt(LocalDateTime.now());
            notifyTaskUpdate(task);
            logger.info("[{}] 跳过：已有文档覆盖", reportId);
            return;
        }

        // 判断是 CREATE 还是 UPDATE
        boolean isUpdate = classifierOutput.contains("\"UPDATE\"") || classifierOutput.contains("UPDATE");
        String targetDoc = extractTargetDoc(classifierOutput);

        // 阶段 3：Generator（Phase 2: 支持 UPDATE 模式）
        task.setStatus(TaskStatus.GENERATING);
        task.setCurrentAgent("generator");
        logger.info("[{}] 开始文档生成（{}）", reportId, isUpdate ? "UPDATE" : "CREATE");

        String generatorPrompt = isUpdate ? buildUpdateGeneratorPrompt() : buildGeneratorPrompt();
        String generatorInput = isUpdate
                ? "请根据以下信息更新已有知识库文档：\n\n提取结果：\n" + extractorOutput
                  + "\n\n分类结果：\n" + classifierOutput
                  + "\n\n目标文档路径：" + targetDoc
                : "请根据以下信息生成知识库文档：\n\n提取结果：\n" + extractorOutput
                  + "\n\n分类结果：\n" + classifierOutput;

        ReactAgent generator = ReactAgent.builder()
                .name("generator_agent")
                .model(agentModel)
                .systemPrompt(generatorPrompt)
                .methodTools(tools)
                .outputKey("generator_result")
                .build();

        OverAllState generatorState = generator.invoke(generatorInput)
                .orElseThrow(() -> new GraphRunnerException("Generator 未返回结果"));

        String generatorOutput = extractOutput(generatorState, "generator_result");
        logger.info("[{}] 文档生成完成: {}", reportId, truncate(generatorOutput, 100));

        // Phase 2: 保存生成内容供审核
        task.setGeneratedContent(generatorOutput);
        task.setGeneratedFilename(extractFilename(generatorOutput, isUpdate, targetDoc));

        // 阶段 4：Indexer — 根据 autoApprove 决定是否需要人工审核
        if (task.isAutoApprove()) {
            // 自动通过：直接入库
            executeIndexing(task);
        } else {
            // 需要人工审核：进入 PENDING_REVIEW 状态
            task.setStatus(TaskStatus.PENDING_REVIEW);
            task.setCurrentAgent("waiting_review");
            notifyTaskUpdate(task);
            notifyReviewRequired(task);
            logger.info("[{}] 等待人工审核", reportId);
        }
    }

    /**
     * Phase 3: WebSocket 通知辅助方法
     */
    private void notifyTaskUpdate(MaintenanceTask task) {
        if (webSocketService != null) {
            try {
                webSocketService.notifyTaskUpdate(task);
            } catch (Exception e) {
                logger.debug("WebSocket 通知失败: {}", e.getMessage());
            }
        }
    }

    private void notifyReviewRequired(MaintenanceTask task) {
        if (webSocketService != null) {
            try {
                webSocketService.notifyReviewRequired(task);
            } catch (Exception e) {
                logger.debug("WebSocket 审核通知失败: {}", e.getMessage());
            }
        }
    }

    private void notifyTaskCompleted(MaintenanceTask task) {
        if (webSocketService != null) {
            try {
                webSocketService.notifyTaskCompleted(task);
            } catch (Exception e) {
                logger.debug("WebSocket 完成通知失败: {}", e.getMessage());
            }
        }
    }

    /**
     * Phase 2: 执行入库（质量检查 + 写入 + 向量化）
     * 文档写入和向量化由服务端直接执行，不通过 Agent 工具调用（避免长文本 JSON 截断）
     */
    private void executeIndexing(MaintenanceTask task) {
        String reportId = task.getTaskId();
        try {
            task.setStatus(TaskStatus.INDEXING);
            task.setCurrentAgent("indexer");
            logger.info("[{}] 开始质量检查与入库", reportId);

            String content = task.getGeneratedContent();
            String filename = task.getGeneratedFilename();

            if (content == null || content.isBlank()) {
                throw new RuntimeException("生成的文档内容为空");
            }
            if (filename == null || filename.isBlank()) {
                filename = "auto_generated_" + System.currentTimeMillis() + ".md";
            }

            // 1. 质量检查（使用 QualityTool 直接调用，不通过 Agent）
            String qualityResult = qualityTool.evaluateQuality(content);
            logger.info("[{}] 质量检查结果: {}", reportId, truncate(qualityResult, 200));

            // 2. 写入文件系统
            String filePath = writeDocumentToFile(filename, content);
            logger.info("[{}] 文档已写入: {}", reportId, filePath);

            // 3. 向量化入库
            try {
                docIndexTool.indexDocument(filePath);
                logger.info("[{}] 向量化入库完成", reportId);
            } catch (Exception e) {
                logger.warn("[{}] 向量化入库失败（文件已写入）: {}", reportId, e.getMessage());
            }

            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            notifyTaskUpdate(task);
            notifyTaskCompleted(task);
        } catch (Exception e) {
            logger.error("[{}] 入库失败", reportId, e);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("入库失败: " + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            notifyTaskUpdate(task);
        }
    }

    /**
     * 直接将文档写入文件系统
     */
    private String writeDocumentToFile(String filename, String content) throws IOException {
        Path dir = Paths.get(config.getReportStoragePath()).getParent();
        if (dir == null) {
            dir = Paths.get("aiops-docs");
        }
        // 确保使用 aiops-docs 目录
        Path knowledgeDir = dir.resolve("aiops-docs");
        if (!Files.exists(knowledgeDir)) {
            Files.createDirectories(knowledgeDir);
        }
        Path filePath = knowledgeDir.resolve(filename);
        Files.writeString(filePath, content);
        return filePath.toString();
    }

    private String readReportContent(String reportId) {
        try {
            Path reportFile = Paths.get(config.getReportStoragePath()).resolve(reportId + ".md");
            if (Files.exists(reportFile)) {
                return Files.readString(reportFile);
            }
        } catch (IOException e) {
            logger.error("读取报告文件失败: {}", reportId, e);
        }
        return null;
    }

    private String extractOutput(OverAllState state, String key) {
        return state.value(key)
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .map(AssistantMessage::getText)
                .orElse("");
    }

    /**
     * 从 Classifier 输出中提取目标文档路径
     */
    private String extractTargetDoc(String classifierOutput) {
        // 简单提取：查找 "targetDoc" 字段值
        try {
            int idx = classifierOutput.indexOf("targetDoc");
            if (idx < 0) return null;
            int start = classifierOutput.indexOf("\"", idx + 10);
            if (start < 0) return null;
            int end = classifierOutput.indexOf("\"", start + 1);
            if (end < 0) return null;
            return classifierOutput.substring(start + 1, end);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Generator 输出中提取文件名
     */
    private String extractFilename(String generatorOutput, boolean isUpdate, String targetDoc) {
        if (isUpdate && targetDoc != null && !targetDoc.isBlank()) {
            return Paths.get(targetDoc).getFileName().toString();
        }
        // 从输出中提取文件名（如果 Agent 输出了文件名）
        try {
            int idx = generatorOutput.indexOf("filename");
            if (idx >= 0) {
                int start = generatorOutput.indexOf("\"", idx + 9);
                if (start >= 0) {
                    int end = generatorOutput.indexOf("\"", start + 1);
                    if (end >= 0) {
                        return generatorOutput.substring(start + 1, end);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "auto_generated_" + System.currentTimeMillis() + ".md";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ==================== Prompt 构建 ====================

    private String buildExtractorPrompt() {
        return """
                你是一个故障信息提取专家。你的任务是从 AIOps 分析报告中提取结构化信息。

                提取规则：
                1. 提取告警名称、级别、类别、症状、根因、修复方案
                2. 提取关键词（用于后续检索）
                3. 评估提取置信度（0~1）
                4. 如果报告信息不足，列出缺失信息
                5. 输出 JSON 格式

                输出格式：
                {
                  "alertName": "告警名称",
                  "alertLevel": "P1/P2/P3/P4",
                  "category": "故障类别",
                  "symptoms": ["症状1", "症状2"],
                  "rootCause": "根因分析",
                  "solution": {"immediate": [], "shortTerm": [], "longTerm": []},
                  "keywords": ["关键词1", "关键词2"],
                  "confidence": 0.85,
                  "missingInfo": []
                }
                """;
    }

    private String buildClassifierPrompt() {
        return """
                你是一个知识库分类专家。判断一个新故障案例应如何处理。

                规则：
                1. 先用 DocSearchTool 搜索知识库，查找相似文档
                2. 用 DocListTool 列出已有文档
                3. 基于搜索结果的相似度判断：
                   - 如果最相似文档与当前案例属于同一类别且症状重叠 ≥ 60%：UPDATE
                   - 如果最相似文档类别不同或症状重叠 < 30%：CREATE
                   - 如果已有文档完全覆盖当前案例信息：SKIP
                4. UPDATE 时必须指定 targetDoc（目标文档路径）

                输出 JSON：
                {
                  "action": "CREATE|UPDATE|SKIP",
                  "targetDoc": "目标文档路径（UPDATE 时必填）",
                  "targetDocTitle": "目标文档标题",
                  "similarity": 0.82,
                  "reason": "分类理由",
                  "newInfoGaps": ["已有文档中缺失但新案例包含的信息"]
                }
                """;
    }

    private String buildGeneratorPrompt() {
        return """
                你是一个运维知识库文档编写专家。根据提取的故障信息生成标准 Markdown 文档。

                规则：
                1. 用 TemplateTool 获取文档模板
                2. 按模板填充内容
                3. 排查步骤必须包含具体工具和命令
                4. 应急处理按时间紧迫度分层
                5. 文档长度 800~2000 字

                输出：完整的 Markdown 文档内容
                """;
    }

    /**
     * Phase 2: UPDATE 模式的 Generator Prompt
     */
    private String buildUpdateGeneratorPrompt() {
        return """
                你是一个运维知识库文档更新专家。你的任务是将新的故障案例信息合并到已有文档中。

                规则：
                1. 先用 DocReadTool 读取目标文档的当前内容（如果工具可用）
                2. 合并策略：
                   - APPEND_SECTION：在对应章节末尾追加新内容
                   - UPDATE_SECTION：更新指定章节，补充新信息
                   - 如果新案例提供了新的根因，追加到"常见根因"章节
                   - 如果新案例的处理方案更优，在对应位置补充
                3. 保留已有文档的所有有效信息
                4. 新增内容用 "（案例 N）" 标注来源
                5. 更新文档末尾的参考信息

                输出：合并后的完整 Markdown 文档内容
                """;
    }
}
