package org.example.ragtest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRecallEvaluatorTest {

    @Test
    void shouldEvaluateRecallPrecisionAndMrr() {
        List<RagTestCase> testCases = RagTestCaseLoader.loadFromClasspath("rag/rag-test-cases.json");

        RagRecallEvaluator evaluator = new RagRecallEvaluator(testCase -> {
            List<RagSearchResult> allResults = buildMockSearchResults(testCase.getQuery());
            return allResults.stream()
                    .sorted(Comparator.comparingDouble(RagSearchResult::getScore))
                    .limit(testCase.getTopK())
                    .collect(Collectors.toList());
        });

        RagMetricsSummary summary = evaluator.evaluate(testCases);

        assertEquals(testCases.size(), summary.getCaseResults().size());
        assertTrue(summary.getAvgRecall() > 0.6, "Avg Recall should be greater than 0.6");
        assertTrue(summary.getAvgPrecision() > 0.4, "Avg Precision should be greater than 0.4");
        assertTrue(summary.getMrr() > 0.7, "MRR should be greater than 0.7");
    }

    private List<RagSearchResult> buildMockSearchResults(String query) {
        Map<String, List<RagSearchResult>> map = Map.of(
                "CPU使用率持续超过80%如何排查", List.of(
                        new RagSearchResult("cpu_high_usage", 0.10),
                        new RagSearchResult("slow_response", 0.22),
                        new RagSearchResult("memory_high_usage", 0.30)
                ),
                "内存持续上涨可能是什么问题", List.of(
                        new RagSearchResult("memory_high_usage", 0.11),
                        new RagSearchResult("slow_response", 0.27),
                        new RagSearchResult("cpu_high_usage", 0.33)
                ),
                "磁盘使用率过高时要看哪些日志", List.of(
                        new RagSearchResult("disk_high_usage", 0.09),
                        new RagSearchResult("service_unavailable", 0.29),
                        new RagSearchResult("cpu_high_usage", 0.34)
                ),
                "服务不可用应该先做什么检查", List.of(
                        new RagSearchResult("service_unavailable", 0.08),
                        new RagSearchResult("slow_response", 0.17),
                        new RagSearchResult("disk_high_usage", 0.28),
                        new RagSearchResult("memory_high_usage", 0.36),
                        new RagSearchResult("cpu_high_usage", 0.41)
                ),
                "接口响应慢怎么定位根因", List.of(
                        new RagSearchResult("slow_response", 0.07),
                        new RagSearchResult("cpu_high_usage", 0.18),
                        new RagSearchResult("memory_high_usage", 0.19),
                        new RagSearchResult("service_unavailable", 0.26),
                        new RagSearchResult("disk_high_usage", 0.39)
                )
        );

        return new ArrayList<>(map.getOrDefault(query, List.of()));
    }
}
