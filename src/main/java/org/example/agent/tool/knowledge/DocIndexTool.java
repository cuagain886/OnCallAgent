package org.example.agent.tool.knowledge;

import org.example.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 向量化入库工具
 * 将文档向量化后写入 Milvus
 */
@Component
public class DocIndexTool {

    private static final Logger logger = LoggerFactory.getLogger(DocIndexTool.class);

    @Autowired
    private VectorIndexService vectorIndexService;

    @Tool(description = "Index a document into the vector database. " +
            "This chunks the document, generates embeddings, and inserts into Milvus. " +
            "Use this after writing a new document to make it searchable.")
    public String indexDocument(
            @ToolParam(description = "The full file path of the document to index") String filePath) {
        try {
            vectorIndexService.indexSingleFile(filePath);
            return "文档已成功索引: " + filePath;
        } catch (Exception e) {
            logger.error("DocIndexTool 索引失败", e);
            return "索引文档失败: " + e.getMessage();
        }
    }

    @Tool(description = "Re-index the entire knowledge base directory. " +
            "Use this when multiple documents have been updated.")
    public String reindexAll() {
        try {
            VectorIndexService.IndexingResult result = vectorIndexService.indexDirectory(null);
            return String.format("全量索引完成：成功 %d 个，失败 %d 个，耗时 %dms",
                    result.getSuccessCount(), result.getFailCount(), result.getDurationMs());
        } catch (Exception e) {
            logger.error("DocIndexTool 全量索引失败", e);
            return "全量索引失败: " + e.getMessage();
        }
    }
}
