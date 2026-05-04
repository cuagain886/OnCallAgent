package org.example.memory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import jakarta.annotation.PostConstruct;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.constant.MilvusConstants;
import org.example.service.VectorEmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 长期记忆管理器
 * 负责管理对话历史的长期存储和检索
 */
@Slf4j
@Service
public class LongTermMemoryManager {

    private static final Gson GSON = new Gson();

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Value("${memory.long-term.enable-auto-save:true}")
    private boolean enableAutoSave;

    @Value("${memory.long-term.save-threshold:3}")
    private int saveThreshold;

    @Value("${memory.long-term.importance-threshold:0.7}")
    private float importanceThreshold;

    @Value("${memory.long-term.content-max-length:" + MilvusConstants.CONTENT_MAX_LENGTH + "}")
    private int contentMaxLength;

    /**
     * 应用启动时预加载 memory collection，避免每次保存时重复调用 loadCollection。
     */
    @PostConstruct
    public void init() {
        try {
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MEMORY_COLLECTION_NAME)
                            .build());
            if (loadResponse.getStatus() == 0) {
                log.info("Memory collection '{}' loaded successfully at startup", MilvusConstants.MEMORY_COLLECTION_NAME);
            } else if (loadResponse.getStatus() == 65535) {
                log.info("Memory collection '{}' already loaded", MilvusConstants.MEMORY_COLLECTION_NAME);
            } else {
                log.warn("Failed to load memory collection at startup: {}", loadResponse.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to load memory collection at startup, will retry on first use", e);
        }
    }

    /**
     * 保存对话到长期记忆
     */
    public void saveConversation(String sessionId, List<Map<String, String>> history) {
        if (!enableAutoSave || history.size() < saveThreshold * 2) {
            return;
        }

        String summary = summarizeConversation(history);
        float importance = evaluateImportance(history.toString().toLowerCase(Locale.ROOT), history.size());
        saveMemoryContent(sessionId, summary, importance, "conversation");
    }

    /**
     * 保存压缩后的会话摘要到长期记忆
     */
    public void saveCompressedConversation(String sessionId, String compressedSummary, List<Map<String, String>> history) {
        if (!enableAutoSave || history == null || history.size() < saveThreshold * 2) {
            return;
        }
        if (compressedSummary == null || compressedSummary.isBlank()) {
            return;
        }

        float importance = evaluateImportance(compressedSummary.toLowerCase(Locale.ROOT), history.size());
        saveMemoryContent(sessionId, compressedSummary, importance, "conversation_summary");
    }

    private void saveMemoryContent(String sessionId, String content, float importance, String type) {
        if (importance < importanceThreshold) {
            log.debug("对话重要性不足，不保存到长期记忆: {}", importance);
            return;
        }

        String safeContent = normalizeForMilvusContent(content);
        if (safeContent.isBlank()) {
            log.warn("记忆内容为空，跳过长期记忆保存: sessionId={}, type={}", sessionId, type);
            return;
        }

        try {
            // 生成向量
            List<Float> vector = embeddingService.generateEmbedding(safeContent);

            // 生成唯一 ID
            String id = generateMemoryId(sessionId);

            // 构建字段数据
            List<InsertParam.Field> fields = new ArrayList<>();

            // ID 字段
            fields.add(new InsertParam.Field("id", Collections.singletonList(id)));

            // session_id 字段（独立字段，支持 Milvus 原生表达式过滤）
            fields.add(new InsertParam.Field(MilvusConstants.MEMORY_SESSION_ID_FIELD, Collections.singletonList(sessionId)));

            // content 字段
            fields.add(new InsertParam.Field("content", Collections.singletonList(safeContent)));

            // vector 字段
            fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));

            // metadata 字段（JSON 对象）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", type);
            metadata.put("sessionId", sessionId);
            metadata.put("importance", importance);
            metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            JsonObject metadataJson = GSON.toJsonTree(metadata).getAsJsonObject();
            fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

            // 构建插入参数（使用独立的 memory collection）
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(MilvusConstants.MEMORY_COLLECTION_NAME)
                    .withFields(fields)
                    .build();

            // 执行插入
            R<MutationResult> insertResponse = milvusClient.insert(insertParam);

            if (insertResponse.getStatus() == 0) {
                log.info("对话已保存到长期记忆: sessionId={}, type={}, importance={}, id={}, contentLength={}",
                        sessionId, type, importance, id, safeContent.length());
            } else {
                log.error("保存对话到长期记忆失败: {}", insertResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("保存对话到长期记忆异常", e);
        }
    }

    /**
     * Milvus VarChar 字段有最大长度限制，超长时进行安全截断。
     */
    private String normalizeForMilvusContent(String content) {
        if (content == null) {
            return "";
        }

        String normalized = content.trim();
        int maxLength = contentMaxLength;
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxLength) {
            return normalized;
        }

        // Milvus 对 VarChar 长度限制在这里按 UTF-8 字节安全截断，避免中文等多字节字符导致超限。
        String suffix = "...(truncated)";
        int suffixBytes = suffix.getBytes(StandardCharsets.UTF_8).length;
        int allowedBytes = Math.max(maxLength - suffixBytes, 0);

        StringBuilder sb = new StringBuilder();
        int usedBytes = 0;
        int index = 0;
        while (index < normalized.length()) {
            int codePoint = normalized.codePointAt(index);
            String cp = new String(Character.toChars(codePoint));
            int cpBytes = cp.getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + cpBytes > allowedBytes) {
                break;
            }
            sb.append(cp);
            usedBytes += cpBytes;
            index += Character.charCount(codePoint);
        }

        String truncated = sb.toString();
        if (allowedBytes > 0 && !truncated.isEmpty()) {
            truncated = truncated + suffix;
        }

        int finalBytes = truncated.getBytes(StandardCharsets.UTF_8).length;
        log.warn("记忆内容超过 Milvus 上限，已按字节截断: originalChars={}, originalBytes={}, finalBytes={}, maxBytes={}",
                normalized.length(), bytes.length, finalBytes, maxLength);
        return truncated;
    }

    /**
     * 检索相关记忆
     */
    public List<Memory> retrieveRelevantMemories(String query, int topK) {
        try {
            // 生成查询向量
            List<Float> queryVector = embeddingService.generateQueryVector(query);

            // 搜索（使用独立的 memory collection）
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MEMORY_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if (searchResponse.getStatus() != 0) {
                log.error("检索长期记忆失败: {}", searchResponse.getMessage());
                return Collections.emptyList();
            }

            // 解析结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<Memory> memories = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                Memory memory = new Memory();
                memory.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                memory.setContent((String) wrapper.getFieldData("content", 0).get(i));
                memory.setScore(wrapper.getIDScore(0).get(i).getScore());
                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                String metadata = metadataObj != null ? metadataObj.toString() : "";
                memory.setMetadata(metadata);
                memory.setTimestamp(extractTimestampFromMetadata(metadata));
                memories.add(memory);
            }

            log.info("检索到 {} 条相关记忆", memories.size());
            return memories;

        } catch (Exception e) {
            log.error("检索长期记忆异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 优先召回同会话的长期记忆，避免被通用知识库内容干扰。
     * 使用 Milvus 原生表达式过滤（session_id 字段），无需 10 倍过采样。
     */
    public List<Memory> retrieveRelevantMemoriesBySession(String query, int topK, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<Float> queryVector = embeddingService.generateQueryVector(query);

            // 使用 Milvus 原生表达式过滤 session_id，精确召回当前会话的记忆
            String filterExpr = MilvusConstants.MEMORY_SESSION_ID_FIELD + " == \"" + sessionId + "\"";

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MEMORY_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withExpr(filterExpr)
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if (searchResponse.getStatus() != 0) {
                log.error("检索同会话长期记忆失败: {}", searchResponse.getMessage());
                return Collections.emptyList();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<Memory> memories = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                Memory memory = new Memory();
                memory.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                memory.setContent((String) wrapper.getFieldData("content", 0).get(i));
                memory.setScore(wrapper.getIDScore(0).get(i).getScore());
                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                String metadata = metadataObj != null ? metadataObj.toString() : "";
                memory.setMetadata(metadata);
                memory.setTimestamp(extractTimestampFromMetadata(metadata));
                memories.add(memory);
            }

            log.info("同会话长期记忆召回: sessionId={}, results={}", sessionId, memories.size());
            return memories;

        } catch (Exception e) {
            log.error("检索同会话长期记忆异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 评估对话重要性。
     * 基于对话长度、多语言关键词命中、高价值模式三个维度综合评分。
     */
    private float evaluateImportance(String content, int historySize) {
        float score = 0.0f;

        // 维度 1：对话长度贡献（最高 0.5 分）
        score += Math.min(historySize / 10.0f, 0.5f);

        // 维度 2：多语言关键词命中（每个 +0.1 分）
        String lowerContent = content.toLowerCase(Locale.ROOT);
        for (String keyword : IMPORTANCE_KEYWORDS) {
            if (lowerContent.contains(keyword)) {
                score += 0.1f;
            }
        }

        // 维度 3：高价值模式额外加分（根因分析类对话价值更高）
        for (String pattern : HIGH_VALUE_PATTERNS) {
            if (lowerContent.contains(pattern)) {
                score += 0.2f;
                break;  // 只加一次
            }
        }

        return Math.min(score, 1.0f);
    }

    // 中文 + 英文 + 技术关键词，全部小写以便大小写无关匹配
    private static final String[] IMPORTANCE_KEYWORDS = {
            // 中文
            "问题", "解决", "错误", "故障", "优化", "建议", "重要", "注意",
            // 英文
            "issue", "fix", "error", "bug", "optimize", "important", "critical", "resolved",
            // 技术关键词
            "exception", "timeout", "oom", "cpu", "memory", "disk", "latency", "oom"
    };

    // 高价值对话模式：根因分析类对话对知识库价值最高
    private static final String[] HIGH_VALUE_PATTERNS = {
            "根因", "root cause", "rca", "根本原因"
    };

    /**
     * 总结对话内容
     */
    private String summarizeConversation(List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("user".equals(role)) {
                sb.append("用户: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                sb.append("助手: ").append(content).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 从 metadata JSON 中提取 timestamp；解析失败时返回空字符串。
     */
    private String extractTimestampFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return "";
        }
        try {
            JsonObject json = GSON.fromJson(metadata, JsonObject.class);
            if (json != null && json.has("timestamp") && !json.get("timestamp").isJsonNull()) {
                return json.get("timestamp").getAsString();
            }
        } catch (Exception e) {
            log.debug("解析记忆 metadata 的 timestamp 失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 生成记忆 ID
     */
    private String generateMemoryId(String sessionId) {
        return "mem_" + sessionId + "_" + System.currentTimeMillis();
    }

    /**
     * 记忆数据类
     */
    @Data
    public static class Memory {
        private String id;
        private String content;
        private float score;
        private String metadata;
        private String timestamp;
    }
}