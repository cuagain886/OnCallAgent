package org.example.agent.tool.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档质量评估工具
 * 对生成的 Markdown 文档进行质量检查
 */
@Component
public class QualityTool {

    private static final Logger logger = LoggerFactory.getLogger(QualityTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] REQUIRED_SECTIONS = {
            "告警名称", "问题描述", "排查步骤", "常见根因", "应急处理", "验证步骤"
    };

    @Tool(description = "Evaluate the quality of a Markdown document against " +
            "the knowledge base standards. Returns a quality score and list of issues. " +
            "Use this before indexing a newly generated document.")
    public String evaluateQuality(
            @ToolParam(description = "The Markdown content to evaluate") String content) {
        try {
            List<String> issues = new ArrayList<>();
            int checksPassed = 0;
            int totalChecks = 7;

            // 检查 1：必要章节
            for (String section : REQUIRED_SECTIONS) {
                if (content.contains(section)) {
                    checksPassed++;
                } else {
                    issues.add("缺少必要章节: " + section);
                }
            }

            // 检查 2：文档长度
            if (content.length() >= 800 && content.length() <= 5000) {
                checksPassed++;
            } else if (content.length() < 800) {
                issues.add("文档过短（" + content.length() + " 字符，建议 800~2000）");
            } else {
                issues.add("文档过长（" + content.length() + " 字符，建议 800~2000）");
            }

            // 检查 3：Markdown 格式
            if (content.contains("# ") && content.contains("\n")) {
                checksPassed++;
            } else {
                issues.add("Markdown 格式不规范（缺少标题或换行）");
            }

            // 检查 4：排查步骤有具体工具
            if (content.contains("```") || content.contains("`") || content.contains("工具")) {
                checksPassed++;
            } else {
                issues.add("排查步骤缺少具体工具或命令");
            }

            float score = (float) checksPassed / totalChecks;
            boolean passed = score >= 0.7f;

            ObjectNode result = MAPPER.createObjectNode();
            result.put("qualityPassed", passed);
            result.put("score", score);
            result.put("checksPassed", checksPassed);
            result.put("totalChecks", totalChecks);
            result.set("issues", MAPPER.valueToTree(issues));

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            logger.error("QualityTool 评估失败", e);
            return "{\"qualityPassed\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
