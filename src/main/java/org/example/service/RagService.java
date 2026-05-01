package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG (Retrieval-Augmented Generation) 服务
 * 结合向量检索和大语言模型生成答案（NVIDIA MiniMax API via OpenAI 兼容接口）
 */
@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    private static final Gson gson = new Gson();

    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${rag.top-k:3}")
    private int topK;

    @Value("${model.rag-model:minimaxai/minimax-m2.7}")
    private String model;

    private HttpClient httpClient;
    private String chatCompletionsUrl;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.chatCompletionsUrl = baseUrl + "/chat/completions";
        logger.info("RAG 服务初始化完成，API endpoint: {}, model: {}, topK: {}", chatCompletionsUrl, model, topK);
    }

    /**
     * 流式处理用户问题（不带历史消息）
     */
    public void queryStream(String question, StreamCallback callback) {
        queryStream(question, new ArrayList<>(), callback);
    }

    /**
     * 流式处理用户问题（带历史消息）
     */
    public void queryStream(String question, List<Map<String, String>> history, StreamCallback callback) {
        try {
            logger.info("收到 RAG 流式查询: {}", question);

            // 1. 从向量数据库检索相关文档
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(question, topK);

            callback.onSearchResults(searchResults);

            if (searchResults.isEmpty()) {
                logger.warn("未找到相关文档");
                callback.onComplete("抱歉，我在知识库中没有找到相关信息来回答您的问题。", "");
                return;
            }

            // 2. 构建上下文和提示词
            String context = buildContext(searchResults);
            String prompt = buildPrompt(question, context);

            // 3. 流式调用 NVIDIA API
            generateAnswerStream(prompt, history, callback);

        } catch (Exception e) {
            logger.error("RAG 流式查询失败", e);
            callback.onError(e);
        }
    }

    /**
     * 构建上下文
     */
    private String buildContext(List<VectorSearchService.SearchResult> searchResults) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            VectorSearchService.SearchResult result = searchResults.get(i);
            context.append("【参考资料 ").append(i + 1).append("】\n");
            context.append(result.getContent()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(String question, String context) {
        return String.format(
                "你是一个专业的AI助手。请根据以下参考资料回答用户的问题。\n\n" +
                        "参考资料：\n%s\n" +
                        "用户问题：%s\n\n" +
                        "请基于上述参考资料给出准确、详细的回答。如果参考资料中没有相关信息，请明确说明。",
                context, question
        );
    }

    /**
     * 通过 NVIDIA OpenAI 兼容接口流式生成答案
     */
    private void generateAnswerStream(String prompt, List<Map<String, String>> history, StreamCallback callback) {
        try {
            // 构建 OpenAI 格式的 messages 数组
            List<Map<String, Object>> messages = new ArrayList<>();

            // 添加历史消息
            for (Map<String, String> historyMsg : history) {
                String role = historyMsg.get("role");
                String content = historyMsg.get("content");
                if ("user".equals(role) || "assistant".equals(role)) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }

            // 添加当前用户问题
            messages.add(Map.of("role", "user", "content", prompt));

            logger.debug("发送给 AI 模型的消息数量: {}（包含 {} 条历史消息）",
                    messages.size(), history.size());

            // 构建请求体
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 1.0,
                    "top_p", 0.95,
                    "max_tokens", 8192,
                    "stream", true
            );

            String jsonBody = gson.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(180))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            logger.info("开始调用 NVIDIA API 流式接口...");

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            StringBuilder reasoningContent = new StringBuilder();
            StringBuilder finalContent = new StringBuilder();

            logger.info("开始接收 AI 模型流式响应...");

            response.body()
                    .filter(line -> line.startsWith("data: "))
                    .map(line -> line.substring(6))
                    .filter(data -> !"[DONE]".equals(data.trim()))
                    .forEach(data -> {
                        try {
                            JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                            var choices = chunk.getAsJsonArray("choices");
                            if (choices != null && !choices.isEmpty()) {
                                JsonObject choice = choices.get(0).getAsJsonObject();
                                JsonObject delta = choice.getAsJsonObject("delta");
                                if (delta != null) {
                                    // 处理思考内容（某些模型返回 reasoning_content）
                                    if (delta.has("reasoning_content")) {
                                        String reasoning = delta.get("reasoning_content").getAsString();
                                        reasoningContent.append(reasoning);
                                        callback.onReasoningChunk(reasoning);
                                    }
                                    // 处理正文内容
                                    if (delta.has("content")) {
                                        String content = delta.get("content").getAsString();
                                        if (content != null && !content.isEmpty()) {
                                            finalContent.append(content);
                                            callback.onContentChunk(content);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("解析 SSE 数据块失败: {}", e.getMessage());
                        }
                    });

            logger.info("AI 模型流式响应完成，总内容长度: {}", finalContent.length());

            callback.onComplete(finalContent.toString(), reasoningContent.toString());
            logger.info("已调用 onComplete 回调");

        } catch (Exception e) {
            logger.error("流式调用 AI 模型失败", e);
            throw new RuntimeException("NVIDIA API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式回调接口
     */
    public interface StreamCallback {
        void onSearchResults(List<VectorSearchService.SearchResult> results);
        void onReasoningChunk(String chunk);
        void onContentChunk(String chunk);
        void onComplete(String fullContent, String fullReasoning);
        void onError(Exception e);
    }
}
