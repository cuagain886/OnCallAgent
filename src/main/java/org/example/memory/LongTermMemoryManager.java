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

    /**
     * 保存对话到长期记忆
     */
    public void saveConversation(String sessionId, List<Map<String, String>> history) {
        if (!enableAutoSave || history.size() < saveThreshold * 2) {
            return;
        }

        try {
            // 评估对话重要性
            float importance = evaluateImportance(history);
            if (importance < importanceThreshold) {
                log.debug("对话重要性不足，不保存到长期记忆: {}", importance);
                return;
            }

            // 总结对话内容
            String summary = summarizeConversation(history);

            // 生成向量
            List<Float> vector = embeddingService.generateEmbedding(summary);

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
            fields.add(new InsertParam.Field("content", Collections.singletonList(summary)));
            
            // vector 字段
            fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));
            
            // metadata 字段（JSON 对象）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "conversation");
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
                log.info("对话已保存到长期记忆: sessionId={}, importance={}, id={}", sessionId, importance, id);
            } else {
                log.error("保存对话到长期记忆失败: {}", insertResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("保存对话到长期记忆异常", e);
        }
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
                    .withOutFields(List.of("id", "content", "metadata", "timestamp"))
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
                memory.setMetadata((String) wrapper.getFieldData("metadata", 0).get(i));
                memory.setTimestamp((String) wrapper.getFieldData("timestamp", 0).get(i));
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
     * 评估对话重要性
     */
    private float evaluateImportance(List<Map<String, String>> history) {
        // 简单实现：基于对话长度和关键词
        float score = 0.0f;

        // 对话长度得分
        score += Math.min(history.size() / 10.0f, 0.5f);

        // 关键词得分
        String content = history.toString().toLowerCase();
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