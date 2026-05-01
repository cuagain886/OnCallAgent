package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内部文档查询工具
 * 使用 RAG (Retrieval-Augmented Generation) 从内部知识库检索相关文档
 */
@Component
public class InternalDocsTools {

    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);

    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";

    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.top-k:3}")
    private int topK = 3;

    @Autowired
    public InternalDocsTools(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    /**
     * 查询内部文档工具 —— 返回格式化纯文本，便于 LLM 直接理解
     */
    @Tool(description = "Use this tool to search internal documentation and knowledge base for relevant information. " +
            "It performs RAG (Retrieval-Augmented Generation) to find similar documents and extract processing steps. " +
            "This is useful when you need to understand internal procedures, best practices, or step-by-step guides " +
            "stored in the company's documentation.")
    public String queryInternalDocs(
            @ToolParam(description = "Search query describing what information you are looking for")
            String query) {

        try {
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(query, topK);

            if (searchResults.isEmpty()) {
                return "未检索到与「" + query + "」相关的文档。";
            }

            return formatResultsAsText(searchResults);

        } catch (Exception e) {
            logger.error("[工具错误] queryInternalDocs 执行失败", e);
            return "查询内部文档时发生错误: " + e.getMessage();
        }
    }

    /**
     * 查询内部文档 —— 返回原始 JSON，供 ChatService 构建 prompt 和 eval API 使用
     */
    public String queryInternalDocsRaw(String query) {
        try {
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(query, topK);

            if (searchResults.isEmpty()) {
                return "[]";
            }

            return objectMapper.writeValueAsString(searchResults);

        } catch (Exception e) {
            logger.error("[工具错误] queryInternalDocsRaw 执行失败", e);
            return "[]";
        }
    }

    /**
     * 将搜索结果格式化为纯文本
     */
    private String formatResultsAsText(List<VectorSearchService.SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("检索到 ").append(results.size()).append(" 条相关文档：\n\n");

        for (int i = 0; i < results.size(); i++) {
            VectorSearchService.SearchResult r = results.get(i);
            sb.append("--- 文档").append(i + 1).append(" ---\n");
            sb.append(r.getContent() != null ? r.getContent().trim() : "(内容为空)").append("\n\n");
        }

        return sb.toString().trim();
    }
}
