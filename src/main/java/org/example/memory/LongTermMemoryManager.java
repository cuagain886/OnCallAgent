package org.example.memory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
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

            // 确保 collection 已加载
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );

            if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
                throw new RuntimeException("加载 collection 失败: " + loadResponse.getMessage());
            }

            // 生成唯一 ID
            String id = generateMemoryId(sessionId);

            // 构建字段数据
            List<InsertParam.Field> fields = new ArrayList<>();

            // ID 字段
            fields.add(new InsertParam.Field("id", Collections.singletonList(id)));

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

            Gson gson = new Gson();
            JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();
            fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

            // 构建插入参数
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
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

            // 搜索
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
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
     */
    public List<Memory> retrieveRelevantMemoriesBySession(String query, int topK, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }

        // biz 集合中混合了文档与记忆，先放大召回再按 metadata 过滤到当前会话。
        int widenedTopK = Math.max(topK * 10, 50);
        List<Memory> candidates = retrieveRelevantMemories(query, widenedTopK);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Memory> filtered = new ArrayList<>();
        for (Memory memory : candidates) {
            if (!isConversationMemory(memory.getMetadata())) {
                continue;
            }
            String memorySessionId = extractSessionIdFromMetadata(memory.getMetadata());
            if (sessionId.equals(memorySessionId)) {
                filtered.add(memory);
                if (filtered.size() >= topK) {
                    break;
                }
            }
        }

        log.info("同会话长期记忆召回: sessionId={}, candidates={}, filtered={}",
                sessionId, candidates.size(), filtered.size());
        return filtered;
    }

    /**
     * 评估对话重要性
     */
    private float evaluateImportance(String content, int historySize) {
        // 简单实现：基于对话长度和关键词
        float score = 0.0f;

        // 对话长度得分
        score += Math.min(historySize / 10.0f, 0.5f);

        // 关键词得分
        String[] keywords = {"问题", "解决", "错误", "故障", "优化", "建议", "重要", "注意"};
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                score += 0.1f;
            }
        }

        return Math.min(score, 1.0f);
    }

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
            JsonObject json = new Gson().fromJson(metadata, JsonObject.class);
            if (json != null && json.has("timestamp") && !json.get("timestamp").isJsonNull()) {
                return json.get("timestamp").getAsString();
            }
        } catch (Exception e) {
            log.debug("解析记忆 metadata 的 timestamp 失败: {}", e.getMessage());
        }
        return "";
    }

    private String extractSessionIdFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return "";
        }
        try {
            JsonObject json = new Gson().fromJson(metadata, JsonObject.class);
            if (json != null && json.has("sessionId") && !json.get("sessionId").isJsonNull()) {
                return json.get("sessionId").getAsString();
            }
        } catch (Exception e) {
            log.debug("解析记忆 metadata 的 sessionId 失败: {}", e.getMessage());
        }
        return "";
    }

    private boolean isConversationMemory(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return false;
        }
        try {
            JsonObject json = new Gson().fromJson(metadata, JsonObject.class);
            if (json == null || !json.has("type") || json.get("type").isJsonNull()) {
                return false;
            }
            String type = json.get("type").getAsString();
            return "conversation".equals(type) || "conversation_summary".equals(type);
        } catch (Exception e) {
            log.debug("解析记忆 metadata 的 type 失败: {}", e.getMessage());
            return false;
        }
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