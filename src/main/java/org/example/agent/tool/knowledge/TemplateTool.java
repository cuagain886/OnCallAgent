package org.example.agent.tool.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文档模板管理工具
 * 提供标准的 AIOps 知识库文档模板
 */
@Component
public class TemplateTool {

    private static final Logger logger = LoggerFactory.getLogger(TemplateTool.class);

    private static final String GENERIC_TEMPLATE = """
            # {category} 处理方案

            ## 告警名称
            - **告警名称**：`{alertName}`
            - **告警级别**：{alertLevel}
            - **触发条件**：{triggerCondition}

            ## 问题描述
            {symptoms}

            ## 排查步骤
            ### 步骤 1：{stepName}
            **工具**：`{toolName}`
            **目的**：{purpose}
            **参数要求**：
            - **查询条件**：`{query}`

            ## 常见根因
            ### 根因 1：{rootCauseName}
            **特征**：{characteristics}
            **处理方案**：{treatment}

            ## 应急处理
            ### 立即执行（5 分钟内）
            {immediateActions}

            ### 短期处理（30 分钟内）
            {shortTermActions}

            ### 长期优化
            {longTermActions}

            ## 验证步骤
            {verificationSteps}

            ## 参考信息
            - **受影响服务**：{affectedServices}
            - **知识来源**：AIOps 自动沉淀
            """;

    private static final Map<String, String> CATEGORY_TEMPLATES = Map.of(
            "CPU高负载", GENERIC_TEMPLATE.replace("{category}", "CPU 高负载"),
            "内存溢出", GENERIC_TEMPLATE.replace("{category}", "内存溢出"),
            "磁盘满", GENERIC_TEMPLATE.replace("{category}", "磁盘空间不足"),
            "服务不可用", GENERIC_TEMPLATE.replace("{category}", "服务不可用"),
            "慢响应", GENERIC_TEMPLATE.replace("{category}", "服务慢响应"),
            "高错误率", GENERIC_TEMPLATE.replace("{category}", "高错误率"),
            "数据库连接池", GENERIC_TEMPLATE.replace("{category}", "数据库连接池耗尽"),
            "消息队列", GENERIC_TEMPLATE.replace("{category}", "消息队列积压"),
            "Redis故障", GENERIC_TEMPLATE.replace("{category}", "Redis 连接故障")
    );

    @Tool(description = "Get the standard document template for a specific " +
            "AIOps scenario category. Returns the Markdown template with placeholders.")
    public String getDocumentTemplate(
            @ToolParam(description = "Category name (e.g., 'CPU高负载', '内存溢出')") String category) {
        String template = CATEGORY_TEMPLATES.getOrDefault(category, GENERIC_TEMPLATE);
        return "以下是「" + category + "」类别的文档模板：\n\n" + template;
    }

    @Tool(description = "List all available document template categories.")
    public String listTemplateCategories() {
        StringBuilder sb = new StringBuilder("可用的文档模板类别：\n\n");
        int i = 1;
        for (String category : CATEGORY_TEMPLATES.keySet()) {
            sb.append(i++).append(". ").append(category).append("\n");
        }
        sb.append("\n使用 getDocumentTemplate(category) 获取对应模板。");
        return sb.toString();
    }
}
