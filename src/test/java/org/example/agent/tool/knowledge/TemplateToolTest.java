package org.example.agent.tool.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TemplateTool 测试
 * 验证模板获取和类别列表功能
 */
class TemplateToolTest {

    private TemplateTool templateTool;

    @BeforeEach
    void setUp() {
        templateTool = new TemplateTool();
    }

    @Test
    void getDocumentTemplate_shouldReturnTemplateForKnownCategory() {
        String result = templateTool.getDocumentTemplate("CPU高负载");

        assertTrue(result.contains("CPU 高负载"), "Should contain category name");
        assertTrue(result.contains("告警名称"), "Should contain standard sections");
        assertTrue(result.contains("排查步骤"), "Should contain investigation steps");
        assertTrue(result.contains("应急处理"), "Should contain emergency actions");
    }

    @Test
    void getDocumentTemplate_shouldReturnGenericForUnknownCategory() {
        String result = templateTool.getDocumentTemplate("未知类别");

        // 应返回通用模板
        assertTrue(result.contains("未知类别") || result.contains("{category}"),
                "Should return template with category placeholder or name");
        assertTrue(result.contains("告警名称"), "Should contain standard sections");
    }

    @Test
    void getDocumentTemplate_shouldContainAllRequiredSections() {
        String result = templateTool.getDocumentTemplate("内存溢出");

        String[] requiredSections = {"告警名称", "问题描述", "排查步骤", "常见根因", "应急处理", "验证步骤"};
        for (String section : requiredSections) {
            assertTrue(result.contains(section), "Template should contain section: " + section);
        }
    }

    @Test
    void listTemplateCategories_shouldListAllCategories() {
        String result = templateTool.listTemplateCategories();

        assertTrue(result.contains("CPU高负载"), "Should list CPU category");
        assertTrue(result.contains("内存溢出"), "Should list memory category");
        assertTrue(result.contains("磁盘满"), "Should list disk category");
        assertTrue(result.contains("服务不可用"), "Should list service unavailable category");
        assertTrue(result.contains("慢响应"), "Should list slow response category");
    }

    @Test
    void listTemplateCategories_shouldReturnNonEmpty() {
        String result = templateTool.listTemplateCategories();
        assertNotNull(result);
        assertTrue(result.length() > 50, "Should return meaningful content");
    }
}
