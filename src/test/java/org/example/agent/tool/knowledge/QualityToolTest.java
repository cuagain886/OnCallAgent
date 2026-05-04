package org.example.agent.tool.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QualityTool 测试
 * 验证文档质量评估的各项检查逻辑
 */
class QualityToolTest {

    private QualityTool qualityTool;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        qualityTool = new QualityTool();
    }

    // ==================== 质量评估测试 ====================

    @Test
    void evaluateQuality_shouldPassGoodDocument() throws Exception {
        String goodDoc = """
                # CPU 高负载处理方案

                ## 告警名称
                - **告警名称**：`CPU_HIGH_USAGE`
                - **告警级别**：P2

                ## 问题描述
                CPU 使用率持续超过 90%，影响服务响应。

                ## 排查步骤
                ### 步骤 1：查看 CPU 使用率
                **工具**：`top`
                **目的**：确认 CPU 使用率

                ## 常见根因
                ### 根因 1：死循环
                **特征**：CPU 占用持续 100%
                **处理方案**：重启服务

                ## 应急处理
                ### 立即执行（5 分钟内）
                1. 重启服务

                ### 短期处理（30 分钟内）
                1. 排查代码

                ### 长期优化
                1. 代码审查

                ## 验证步骤
                1. 监控 CPU 使用率
                """;

        String result = qualityTool.evaluateQuality(goodDoc);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("qualityPassed").asBoolean(), "Good document should pass");
        assertTrue(json.get("score").floatValue() >= 0.7f, "Score should be >= 0.7");
    }

    @Test
    void evaluateQuality_shouldFailShortDocument() throws Exception {
        String shortDoc = "# 短文档\n\n内容很少。";

        String result = qualityTool.evaluateQuality(shortDoc);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("qualityPassed").asBoolean(), "Short document should fail");
        assertTrue(json.get("score").floatValue() < 0.7f);
    }

    @Test
    void evaluateQuality_shouldDetectMissingSections() throws Exception {
        String incompleteDoc = """
                # 处理方案

                ## 告警名称
                测试告警

                ## 问题描述
                这是一个测试。
                """;

        String result = qualityTool.evaluateQuality(incompleteDoc);
        JsonNode json = mapper.readTree(result);

        // 缺少排查步骤、常见根因、应急处理、验证步骤
        JsonNode issues = json.get("issues");
        assertTrue(issues.isArray());
        assertTrue(issues.size() > 0, "Should have issues for missing sections");
    }

    @Test
    void evaluateQuality_shouldDetectNoMarkdownHeaders() throws Exception {
        String noHeaders = "这是一段纯文本，没有任何 Markdown 标题格式。\n".repeat(50);

        String result = qualityTool.evaluateQuality(noHeaders);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("qualityPassed").asBoolean());
    }

    @Test
    void evaluateQuality_shouldReturnValidJson() throws Exception {
        String doc = "# 测试\n\n## 告警名称\n## 问题描述\n## 排查步骤\n## 常见根因\n## 应急处理\n## 验证步骤\n\n" + "内容。".repeat(100);

        String result = qualityTool.evaluateQuality(doc);

        // 应该是合法的 JSON
        JsonNode json = mapper.readTree(result);
        assertNotNull(json.get("qualityPassed"));
        assertNotNull(json.get("score"));
        assertNotNull(json.get("checksPassed"));
        assertNotNull(json.get("totalChecks"));
        assertNotNull(json.get("issues"));
    }
}
