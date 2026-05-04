package org.example.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.Hooks.MemoryPersistHook;
import org.example.Hooks.MemoryRecallHook;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    private static final Pattern DOC_QUERY_PATTERN = Pattern.compile(
            "(文档|知识库|流程|步骤|排查|指南|规范|SOP|怎么做|如何|手册|最佳实践|内部资料|RAG)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern METRIC_QUERY_PATTERN = Pattern.compile(
            "(告警|prometheus|指标|cpu|memory|mem|磁盘|disk|日志|log|qps|延迟|latency)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPS_SYMPTOM_PATTERN = Pattern.compile(
            "(内存|cpu|磁盘|慢响应|响应慢|超时|不可用|抖动|上涨|飙升|泄漏|高负载|告警|故障|异常)",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)
    private QueryLogsTools queryLogsTools;

    @Autowired
    private ToolCallbackProvider tools;

    @Autowired(required = false)
    private MemoryRecallHook memoryRecallHook;

    @Autowired(required = false)
    private MemoryPersistHook memoryPersistHook;

    private volatile String lastRagResult;

    public String getLastRagResult() {
        return lastRagResult;
    }

    @Value("${memory.hooks.enabled:true}")
    private boolean memoryHooksEnabled;

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.base-url}")
    private String openAiBaseUrl;

    @Value("${model.chat-model}")
    private String chatModel;

    @Value("${model.rag-model}")
    private String ragModel;

    @Value("${model.aiops-model}")
    private String aiOpsModel;

    @Value("${model.aiops-supervisor-model:minimaxai/minimax-m2.7}")
    private String aiOpsSupervisorModel;

    /**
     * 创建 OpenAI API 实例（指向 NVIDIA 兼容接口）
     */
    public OpenAiApi createOpenAiApi() {
        return OpenAiApi.builder()
                .baseUrl(openAiBaseUrl)
                .apiKey(openAiApiKey)
                .build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     * @param modelName 指定的模型名称
     */
    public OpenAiChatModel createChatModel(OpenAiApi openAiApi, double temperature, int maxToken, double topP, String modelName) {
        String finalModelName = (modelName != null && !modelName.isEmpty()) ? modelName : this.chatModel;
        logger.debug("创建 ChatModel，使用模型: {}", finalModelName);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(finalModelName)
                        .temperature(temperature)
                        .maxTokens(maxToken)
                        .topP(topP)
                        .extraBody(null)
                        .build())
                .build();
    }

    /**
     * 创建 ChatModel（使用配置的对话模型作为默认）
     */
    public OpenAiChatModel createChatModel(OpenAiApi openAiApi, double temperature, int maxToken, double topP) {
        return createChatModel(openAiApi, temperature, maxToken, topP, this.chatModel);
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public OpenAiChatModel createStandardChatModel(OpenAiApi openAiApi) {
        return createChatModel(openAiApi, 0.7, 2000, 0.9, getChatModelName());
    }

    /**
     * 创建 RAG 专用 ChatModel
     */
    public OpenAiChatModel createRagChatModel(OpenAiApi openAiApi) {
        return createChatModel(openAiApi, 0.7, 2000, 0.9, this.ragModel);
    }

    /**
     * 创建 AI Ops 专用 ChatModel（低温度提高确定性）
     */
    public OpenAiChatModel createAiOpsChatModel(OpenAiApi openAiApi) {
        return createChatModel(openAiApi, 0.3, 2048, 0.9, getAiOpsModelName());
    }

    /**
     * 创建 AI Ops Supervisor 专用 ChatModel（仅做路由决策，需稳定输出 JSON）
     */
    public OpenAiChatModel createAiOpsSupervisorChatModel(OpenAiApi openAiApi) {
        return createChatModel(openAiApi, 0.0, 128, 0.1, this.aiOpsSupervisorModel);
    }

    public String getChatModelName() {
        return this.chatModel;
    }

    public String getRagModelName() {
        return this.ragModel;
    }

    public String getAiOpsModelName() {
        return this.aiOpsModel;
    }

    public String getAiOpsSupervisorModelName() {
        return this.aiOpsSupervisorModel;
    }

    /**
     * 构建系统提示词（仅系统指令，不含历史消息）。
     * 当记忆 Hooks 启用时使用此方法，历史对话由 MemoryRecallHook 管理，
     * 避免系统提示词中的历史与 Hook 召回的长期记忆上下文产生冗余。
     */
    public String buildSystemPrompt() {
        return buildSystemPromptInternal(null);
    }

    /**
     * 构建系统提示词（包含历史消息）。
     * 当记忆 Hooks 关闭时使用此方法，历史对话需要嵌入系统提示词中。
     */
    public String buildSystemPrompt(List<Map<String, String>> history) {
        return buildSystemPromptInternal(history);
    }

    private String buildSystemPromptInternal(List<Map<String, String>> history) {
        StringBuilder systemPromptBuilder = new StringBuilder();

        systemPromptBuilder.append("【严格规则】\n");
        systemPromptBuilder.append("1. 绝对禁止在任何回复中使用任何 emoji 表情符号、特殊符号或颜文字\n");

        systemPromptBuilder.append("你是一个专业的智能助手，可以获取当前时间、查询公司内部文档知识库、查询系统监控指标和 Prometheus 告警。\n");
        systemPromptBuilder.append("\n【可用工具】：\n");
        systemPromptBuilder.append("1. getCurrentDateTime - 获取当前日期和时间\n");
        systemPromptBuilder.append("2. queryInternalDocs - 查询公司内部文档和知识库（使用 RAG 技术从本地存储的 Markdown 文档中检索）\n");
        systemPromptBuilder.append("3. QueryMetric/QueryRangeMetric - 查询 Prometheus 监控指标\n");
        systemPromptBuilder.append("4. 腾讯云 MCP 工具 - 用于查询告警、日志、事件等\n");
        systemPromptBuilder.append("\n【使用建议】：\n");
        systemPromptBuilder.append("- 当用户询问时间相关问题时，使用 getCurrentDateTime。\n");
        systemPromptBuilder.append("- 当用户需要查询公司内部流程、最佳实践、技术指南或故障排查步骤时，使用 queryInternalDocs。\n");
        systemPromptBuilder.append("- 当用户需要查询系统监控数据或告警信息时，使用 QueryMetric 工具或腾讯云 MCP 工具。\n");
        systemPromptBuilder.append("- 优先使用本地文档知识库（queryInternalDocs）回答与公司内部流程、最佳实践相关的问题。\n\n");

        if (history != null && !history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }

        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");

        return systemPromptBuilder.toString();
    }

    /**
     * 动态构建方法工具数组
     */
    public Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
        } else {
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools};
        }
    }

    /**
     * 获取所有工具回调列表
     */
    public ToolCallback[] getAllToolCallbacks() {
        ToolCallback[] mcpTools = tools.getToolCallbacks();
        logger.info("MCP 工具总数: {}", mcpTools.length);
        logger.info("本地方法工具将通过 methodTools 参数注入");
        return mcpTools;
    }

    /**
     * 记录可用工具列表
     */
    public void logAvailableTools() {
        logger.info("可用工具列表 - 本地方法工具:");
        Object[] methodTools = buildMethodToolsArray();
        for (Object tool : methodTools) {
            logger.info(">>> {}", tool.getClass().getSimpleName());
        }

        logger.info("可用工具列表 - MCP 工具:");
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        if (toolCallbacks.length == 0) {
            logger.info("（未提供 MCP 工具）");
        } else {
            for (ToolCallback toolCallback : toolCallbacks) {
                logger.info(">>> {}", toolCallback.getToolDefinition().name());
            }
        }
    }

    /**
     * 创建 ReactAgent
     */
    public ReactAgent createReactAgent(ChatModel chatModel, String systemPrompt) {
        Object[] methodTools = buildMethodToolsArray();
        ToolCallback[] mcpTools = getAllToolCallbacks();

        logger.info("正在构建 ReactAgent...");
        logger.info("本地方法工具数: {}", methodTools.length);
        logger.info("MCP 工具数: {}", mcpTools.length);

        var builder = ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(methodTools)
                .tools(mcpTools);

        if (memoryHooksEnabled && memoryRecallHook != null && memoryPersistHook != null) {
            builder.hooks(memoryRecallHook, memoryPersistHook);
            logger.info("已启用记忆 Hooks: {}, {}", memoryRecallHook.getName(), memoryPersistHook.getName());
        } else {
            logger.info("记忆 Hooks 未启用或未注入，将使用默认对话流程");
        }

        ReactAgent agent = builder.build();

        logger.info("✓ ReactAgent 构建完成");
        return agent;
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        return executeChat(agent, question, null);
    }

    /**
     * 执行 ReactAgent 对话（非流式），并传入会话配置供 Hook 使用
     */
    public String executeChat(ReactAgent agent, String question, String sessionId) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");

        String RecalledQuestion = buildQuestionWithForcedRecall(question);
        RunnableConfig config = buildChatRunnableConfig(sessionId);
        var response = agent.call(RecalledQuestion, config);

        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());

        try {
            logTokenUsage(response);
        } catch (Exception e) {
            logger.debug("无法提取token使用信息: {}", e.getMessage());
        }

        return answer;
    }

    /**
     * 构建会话级 RunnableConfig
     */
    public RunnableConfig buildChatRunnableConfig(String sessionId) {
        RunnableConfig.Builder builder = RunnableConfig.builder();
        if (sessionId != null && !sessionId.isBlank()) {
            builder.threadId(sessionId);
            builder.addMetadata("sessionId", sessionId);
        }
        return builder.build();
    }

    /**
     * 为工具路由增强用户问题
     */
    public String enhanceQuestionForToolRouting(String question) {
        if (question == null || question.isBlank()) {
            return question;
        }

        boolean docLike = DOC_QUERY_PATTERN.matcher(question).find();
        boolean metricLike = METRIC_QUERY_PATTERN.matcher(question).find();

        if (docLike && !metricLike) {
            logger.info("命中文档检索路由规则，增强提示以触发 queryInternalDocs");
            return "请先调用工具 queryInternalDocs 检索内部知识库，再基于检索结果回答；若无结果请明确说明。\n用户问题：" + question;
        }

        return question;
    }

    /**
     * 运维症状类问题优先执行一次 RAG 预检索
     */
    public String prepareQuestionWithRagPrefetch(String question) {
        String routedQuestion = enhanceQuestionForToolRouting(question);
        if (question == null || question.isBlank()) {
            return routedQuestion;
        }

        if (!OPS_SYMPTOM_PATTERN.matcher(question).find()) {
            return routedQuestion;
        }

        try {
            String ragJson = internalDocsTools.queryInternalDocsRaw(question);
            this.lastRagResult = ragJson;
            String extractedText = extractTextFromRagJson(ragJson);
            logger.info("已执行 RAG 预检索，提取文本长度: {}", extractedText.length());

            if (extractedText.isEmpty()) {
                return "【参考资料】\n（未检索到相关文档）\n\n"
                        + "【要求】\n"
                        + "- 根据你的通用知识简要回答\n"
                        + "- 回答控制在 300 字以内\n\n"
                        + "【用户问题】" + question;
            }

            return "【参考资料】\n" + extractedText
                    + "\n【要求】\n"
                    + "- 优先基于以上参考资料回答，若信息不足可补充通用排查建议\n"
                    + "- 回答控制在 300 字以内\n"
                    + "- 直接给出答案，不要复述问题\n\n"
                    + "【用户问题】" + question;
        } catch (Exception e) {
            logger.warn("RAG 预检索失败，回退到普通工具路由: {}", e.getMessage());
            return routedQuestion;
        }
    }

    private String limitLength(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }

    /**
     * 从响应对象中提取并记录token使用情况
     */
    private void logTokenUsage(Object response) {
        try {
            java.lang.reflect.Method getOutputMethod = null;

            try {
                getOutputMethod = response.getClass().getMethod("output");
                Object output = getOutputMethod.invoke(response);

                java.lang.reflect.Method getUsageFromOutput = output.getClass().getMethod("getUsage");
                Object usage = getUsageFromOutput.invoke(output);

                if (usage != null) {
                    long inputTokens = getFieldValue(usage, "input_tokens", 0L);
                    long outputTokens = getFieldValue(usage, "output_tokens", 0L);
                    long totalTokens = inputTokens + outputTokens;

                    logger.info("【Token 统计】输入tokens: {}, 输出tokens: {}, 总计: {}",
                            inputTokens, outputTokens, totalTokens);
                }
            } catch (Exception e) {
                logger.debug("尝试从output获取usage失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.debug("提取token使用信息异常: {}", e.getMessage());
        }
    }

    private long getFieldValue(Object obj, String fieldName, long defaultValue) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception e) {
            logger.debug("无法获取字段 {}: {}", fieldName, e.getMessage());
        }
        return defaultValue;
    }

    /*
     * 根据问题在知识库查询然后召回，优化后的 prompt 构建
     */
    public String buildQuestionWithForcedRecall(String question) {
        String ragJson = internalDocsTools.queryInternalDocsRaw(question);
        this.lastRagResult = ragJson;

        String extractedText = extractTextFromRagJson(ragJson);

        if (extractedText.isEmpty()) {
            return "【参考资料】\n（未检索到相关文档）\n\n"
                    + "【要求】\n"
                    + "- 根据你的通用知识简要回答\n"
                    + "- 若无法确定，请明确说根据现有资料无法确定\n"
                    + "- 回答控制在 300 字以内\n\n"
                    + "【用户问题】" + question;
        }

        return "【参考资料】\n" + extractedText
                + "\n【要求】\n"
                + "- 基于参考资料概括回答，提炼与问题直接相关的关键信息\n"
                + "- 回答控制在 200 字以内，只保留核心要点\n"
                + "- 直接给出答案，不要复述问题、不要展开排查步骤、不要添加客套话\n"
                + "- 若信息不足，明确说根据现有资料无法确定\n\n"
                + "【用户问题】" + question;
    }

    /**
     * 从 RAG JSON 中提取纯文本内容，去除 id/score/metadata 等元数据
     */
    private String extractTextFromRagJson(String ragJson) {
        if (ragJson == null || ragJson.isBlank()) {
            return "";
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(ragJson);

            // 处理 no_results / error 状态
            if (root.isObject() && root.has("status")) {
                String status = root.get("status").asText();
                if ("no_results".equals(status) || "error".equals(status)) {
                    return "";
                }
            }

            if (!root.isArray() || root.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < root.size(); i++) {
                JsonNode node = root.get(i);
                String content = node.has("content") ? node.get("content").asText("") : "";
                if (content.isBlank()) {
                    continue;
                }
                sb.append("--- 文档").append(i + 1).append(" ---\n");
                sb.append(content.trim()).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            logger.warn("解析 RAG JSON 失败，回退到原始内容: {}", e.getMessage());
            return limitLength(ragJson, 4000);
        }
    }
}
