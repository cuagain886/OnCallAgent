package org.example.agent.tool.knowledge;

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
 * 知识库语义搜索工具
 * 供 Agent 在知识库维护流程中检索已有文档
 */
@Component
public class DocSearchTool {

    private static final Logger logger = LoggerFactory.getLogger(DocSearchTool.class);

    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @Tool(description = "Search the knowledge base using semantic similarity. " +
            "Returns the most relevant documents with their titles and content summaries. " +
            "Use this to check if a similar document already exists before creating a new one.")
    public String searchKnowledgeBase(
            @ToolParam(description = "Search query describing what you're looking for") String query,
            @ToolParam(description = "Number of results to return (default 5)") int topK) {
        try {
            int k = topK > 0 ? topK : defaultTopK;
            List<VectorSearchService.SearchResult> results =
                    vectorSearchService.searchSimilarDocuments(query, k);

            if (results.isEmpty()) {
                return "未找到与「" + query + "」相关的文档。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(results.size()).append(" 个相关文档：\n\n");
            for (int i = 0; i < results.size(); i++) {
                VectorSearchService.SearchResult r = results.get(i);
                sb.append(i + 1).append(". [相似度: ").append(String.format("%.2f", 1.0 - r.getScore())).append("] ");
                sb.append(r.getId()).append("\n");
                String content = r.getContent();
                if (content != null && !content.isBlank()) {
                    String preview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    sb.append("   摘要：").append(preview.trim()).append("\n\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            logger.error("DocSearchTool 搜索失败", e);
            return "搜索知识库时发生错误: " + e.getMessage();
        }
    }
}
