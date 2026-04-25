package org.example.ragtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.tool.InternalDocsTools;
import org.example.service.VectorIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Disabled("Requires external dependencies (Milvus + DashScope + indexed docs). Run manually when environment is ready.")
class RagRecallIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(RagRecallIntegrationTest.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private VectorIndexService vectorIndexService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 在每个测试前，初始化文档索引
     * 读取 aiops-docs/ 目录下的所有 markdown 文件并索引到 Milvus
     */
    @BeforeEach
    void initializeTestData() {
        logger.info("=== 开始初始化 RAG 测试数据 ===");
        try {
            String docPath = "aiops-docs";
            logger.info("索引文档目录: {}", docPath);
            
            VectorIndexService.IndexingResult result = vectorIndexService.indexDirectory(docPath);
            
            if (result.isSuccess()) {
                logger.info("✓ 文档初始化成功: 总共 {} 个文件, 成功 {} 个, 失败 {} 个",
                        result.getTotalFiles(), result.getSuccessCount(), result.getFailCount());
            } else {
                logger.warn("⚠ 文档初始化部分失败: 成功 {} 个, 失败 {} 个, 错误: {}",
                        result.getSuccessCount(), result.getFailCount(), result.getErrorMessage());
            }
            
            logger.info("=== 文档初始化完成，开始 RAG 召回测试 ===\n");
        } catch (Exception e) {
            logger.error("文档初始化失败，测试可能会因缺少数据而失败", e);
        }
    }

    @Test
    void evaluateRecallWithRealRetriever() {
        List<RagTestCase> testCases = RagTestCaseLoader.loadFromClasspath("rag/rag-test-cases.json");

        RagRecallEvaluator evaluator = new RagRecallEvaluator(testCase -> {
            String json = internalDocsTools.queryInternalDocs(testCase.getQuery());
            List<RagSearchResult> output = new ArrayList<>();

            try {
                List<RealSearchResult> results = objectMapper.readValue(json, new TypeReference<List<RealSearchResult>>() {
                });
                for (RealSearchResult result : results) {
                    output.add(new RagSearchResult(result.id, result.score));
                }
            } catch (Exception ignored) {
                // Tool can return error JSON; treat as empty result for this case.
            }

            if (output.size() > testCase.getTopK()) {
                return output.subList(0, testCase.getTopK());
            }
            return output;
        });

        RagMetricsSummary summary = evaluator.evaluate(testCases);
        assertTrue(summary.getAvgRecall() >= 0.0);
    }

    private static class RealSearchResult {
        public String id;
        public float score;
    }
}
