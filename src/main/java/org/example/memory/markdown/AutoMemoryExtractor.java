package org.example.memory.markdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 自动记忆提取器
 *
 * 在对话结束后，使用 LLM 回顾对话，提取记忆，写入 Markdown 文件。
 * 支持四种记忆类型：USER、PROJECT、FEEDBACK、REFERENCE
 */
@Component
public class AutoMemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(AutoMemoryExtractor.class);

    private final MarkdownMemoryManager memoryManager;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public AutoMemoryExtractor(MarkdownMemoryManager memoryManager,
                                ChatModel chatModel,
                                ObjectMapper objectMapper) {
        this.memoryManager = memoryManager;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步提取记忆
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param messages  对话消息列表
     */
    @Async
    public void extractFromConversation(String userId, String sessionId,
                                         List<Map<String, String>> messages) {
        if (!memoryManager.isEnabled()) {
            return;
        }

        try {
            // Step 1: 使用 LLM 分析对话
            String conversation = formatConversation(messages);
            ExtractionResult result = analyzeConversation(conversation);

            if (result == null) {
                log.debug("No memories extracted from conversation");
                return;
            }

            // Step 2: 保存各种类型的记忆
            if (result.hasUserInsights()) {
                saveUserMemory(userId, result);
            }

            if (result.hasProjectInsights()) {
                saveProjectMemory(userId, result);
            }

            if (result.hasFeedback()) {
                saveFeedbackMemory(userId, result);
            }

            if (result.hasReferences()) {
                saveReferenceMemory(userId, result);
            }

            log.info("Extracted memories for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to extract memories", e);
        }
    }

    /**
     * 格式化对话内容
     */
    private String formatConversation(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : messages) {
            String role = msg.getOrDefault("role", "unknown");
            String content = msg.getOrDefault("content", "");
            sb.append(role).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }

    /**
     * 使用 LLM 分析对话，提取记忆
     */
    private ExtractionResult analyzeConversation(String conversation) {
        String prompt = buildExtractionPrompt(conversation);

        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            return parseExtractionResult(response);
        } catch (Exception e) {
            log.warn("Failed to analyze conversation", e);
            return null;
        }
    }

    /**
     * 构建提取提示词
     */
    private String buildExtractionPrompt(String conversation) {
        return String.format("""
                分析以下对话，提取值得记忆的信息。按类型分类返回。

                对话内容：
                %s

                请提取以下类型的记忆（JSON格式）：

                {
                    "user": {
                        "preferences": ["偏好1", "偏好2"],
                        "workStyle": "工作风格描述",
                        "techLevel": "技术水平描述"
                    },
                    "project": {
                        "techStack": ["技术1", "技术2"],
                        "conventions": ["约定1", "约定2"],
                        "architecture": "架构描述"
                    },
                    "feedback": [
                        {
                            "type": "correction|confirmation",
                            "content": "具体反馈内容",
                            "context": "发生场景"
                        }
                    ],
                    "reference": [
                        {
                            "name": "资源名称",
                            "type": "doc|url|tool",
                            "location": "位置/URL",
                            "description": "描述"
                        }
                    ]
                }

                提取标准：
                - user: 用户明确表达的偏好、观察到的工作习惯、技术水平体现
                - project: 技术栈信息、架构约定、编码规范
                - feedback: 用户的纠正（"不要这样做"）或确认（"对，就是这样"）
                - reference: 用户提到的外部文档、工具、资源位置

                如果某个类型没有相关信息，返回空对象或空数组。
                只返回JSON，不要有其他内容。
                """, conversation);
    }

    /**
     * 解析提取结果
     */
    private ExtractionResult parseExtractionResult(String response) {
        try {
            // 提取 JSON 部分
            String json = response.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');

            if (start == -1 || end == -1 || start >= end) {
                log.warn("Invalid extraction response format");
                return null;
            }

            json = json.substring(start, end + 1);
            JsonNode root = objectMapper.readTree(json);

            ExtractionResult result = new ExtractionResult();

            // 解析 user
            if (root.has("user") && !root.get("user").isEmpty()) {
                JsonNode userNode = root.get("user");
                if (userNode.has("preferences")) {
                    result.setUserPreferences(getStringList(userNode.get("preferences")));
                }
                if (userNode.has("workStyle")) {
                    result.setUserWorkStyle(userNode.get("workStyle").asText());
                }
                if (userNode.has("techLevel")) {
                    result.setUserTechLevel(userNode.get("techLevel").asText());
                }
            }

            // 解析 project
            if (root.has("project") && !root.get("project").isEmpty()) {
                JsonNode projectNode = root.get("project");
                if (projectNode.has("techStack")) {
                    result.setProjectTechStack(getStringList(projectNode.get("techStack")));
                }
                if (projectNode.has("conventions")) {
                    result.setProjectConventions(getStringList(projectNode.get("conventions")));
                }
                if (projectNode.has("architecture")) {
                    result.setProjectArchitecture(projectNode.get("architecture").asText());
                }
            }

            // 解析 feedback
            if (root.has("feedback") && root.get("feedback").isArray()) {
                for (JsonNode fbNode : root.get("feedback")) {
                    FeedbackItem item = new FeedbackItem();
                    item.setType(fbNode.has("type") ? fbNode.get("type").asText() : "correction");
                    item.setContent(fbNode.has("content") ? fbNode.get("content").asText() : "");
                    item.setContext(fbNode.has("context") ? fbNode.get("context").asText() : "");
                    result.addFeedback(item);
                }
            }

            // 解析 reference
            if (root.has("reference") && root.get("reference").isArray()) {
                for (JsonNode refNode : root.get("reference")) {
                    ReferenceItem item = new ReferenceItem();
                    item.setName(refNode.has("name") ? refNode.get("name").asText() : "");
                    item.setType(refNode.has("type") ? refNode.get("type").asText() : "doc");
                    item.setLocation(refNode.has("location") ? refNode.get("location").asText() : "");
                    item.setDescription(refNode.has("description") ? refNode.get("description").asText() : "");
                    result.addReference(item);
                }
            }

            return result;
        } catch (Exception e) {
            log.warn("Failed to parse extraction result", e);
            return null;
        }
    }

    private List<String> getStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    /**
     * 保存用户记忆
     */
    private void saveUserMemory(String userId, ExtractionResult result) {
        StringBuilder content = new StringBuilder();
        content.append("## 用户偏好\n");
        if (result.getUserPreferences() != null) {
            for (String pref : result.getUserPreferences()) {
                content.append("- ").append(pref).append("\n");
            }
        }
        content.append("\n## 工作风格\n");
        if (result.getUserWorkStyle() != null) {
            content.append(result.getUserWorkStyle()).append("\n");
        }
        content.append("\n## 技术水平\n");
        if (result.getUserTechLevel() != null) {
            content.append(result.getUserTechLevel()).append("\n");
        }

        String fileName = "user_" + userId;
        memoryManager.saveMemory(MemoryType.USER, fileName,
                "User Profile - " + userId,
                "用户画像信息",
                content.toString());
    }

    /**
     * 保存项目记忆
     */
    private void saveProjectMemory(String userId, ExtractionResult result) {
        StringBuilder content = new StringBuilder();
        content.append("## 技术栈\n");
        if (result.getProjectTechStack() != null) {
            for (String tech : result.getProjectTechStack()) {
                content.append("- ").append(tech).append("\n");
            }
        }
        content.append("\n## 约定\n");
        if (result.getProjectConventions() != null) {
            for (String conv : result.getProjectConventions()) {
                content.append("- ").append(conv).append("\n");
            }
        }
        content.append("\n## 架构\n");
        if (result.getProjectArchitecture() != null) {
            content.append(result.getProjectArchitecture()).append("\n");
        }

        String fileName = "project_" + userId;
        memoryManager.saveMemory(MemoryType.PROJECT, fileName,
                "Project Context - " + userId,
                "项目上下文信息",
                content.toString());
    }

    /**
     * 保存反馈记忆
     */
    private void saveFeedbackMemory(String userId, ExtractionResult result) {
        for (FeedbackItem item : result.getFeedbacks()) {
            StringBuilder content = new StringBuilder();
            String icon = "correction".equals(item.getType()) ? "❌" : "✅";
            content.append("## ").append(icon).append(" ").append(item.getContent()).append("\n\n");
            content.append("**场景**: ").append(item.getContext()).append("\n");
            content.append("**时间**: ").append(LocalDate.now()).append("\n");

            String fileName = "feedback_" + userId + "_" + System.currentTimeMillis();
            memoryManager.saveMemory(MemoryType.FEEDBACK, fileName,
                    "Feedback - " + item.getContent().substring(0, Math.min(30, item.getContent().length())),
                    item.getContext(),
                    content.toString());
        }
    }

    /**
     * 保存引用记忆
     */
    private void saveReferenceMemory(String userId, ExtractionResult result) {
        StringBuilder content = new StringBuilder();
        content.append("## 外部资源\n\n");
        for (ReferenceItem item : result.getReferences()) {
            content.append("### ").append(item.getName()).append("\n");
            content.append("- **类型**: ").append(item.getType()).append("\n");
            content.append("- **位置**: ").append(item.getLocation()).append("\n");
            content.append("- **描述**: ").append(item.getDescription()).append("\n\n");
        }

        String fileName = "reference_" + userId;
        memoryManager.saveMemory(MemoryType.REFERENCE, fileName,
                "External References - " + userId,
                "外部资源索引",
                content.toString());
    }

    /**
     * 提取结果内部类
     */
    private static class ExtractionResult {
        private List<String> userPreferences;
        private String userWorkStyle;
        private String userTechLevel;
        private List<String> projectTechStack;
        private List<String> projectConventions;
        private String projectArchitecture;
        private List<FeedbackItem> feedbacks = new java.util.ArrayList<>();
        private List<ReferenceItem> references = new java.util.ArrayList<>();

        // Getters and setters
        public List<String> getUserPreferences() { return userPreferences; }
        public void setUserPreferences(List<String> userPreferences) { this.userPreferences = userPreferences; }
        public String getUserWorkStyle() { return userWorkStyle; }
        public void setUserWorkStyle(String userWorkStyle) { this.userWorkStyle = userWorkStyle; }
        public String getUserTechLevel() { return userTechLevel; }
        public void setUserTechLevel(String userTechLevel) { this.userTechLevel = userTechLevel; }
        public List<String> getProjectTechStack() { return projectTechStack; }
        public void setProjectTechStack(List<String> projectTechStack) { this.projectTechStack = projectTechStack; }
        public List<String> getProjectConventions() { return projectConventions; }
        public void setProjectConventions(List<String> projectConventions) { this.projectConventions = projectConventions; }
        public String getProjectArchitecture() { return projectArchitecture; }
        public void setProjectArchitecture(String projectArchitecture) { this.projectArchitecture = projectArchitecture; }
        public List<FeedbackItem> getFeedbacks() { return feedbacks; }
        public void addFeedback(FeedbackItem item) { this.feedbacks.add(item); }
        public List<ReferenceItem> getReferences() { return references; }
        public void addReference(ReferenceItem item) { this.references.add(item); }

        public boolean hasUserInsights() {
            return (userPreferences != null && !userPreferences.isEmpty())
                    || userWorkStyle != null
                    || userTechLevel != null;
        }

        public boolean hasProjectInsights() {
            return (projectTechStack != null && !projectTechStack.isEmpty())
                    || (projectConventions != null && !projectConventions.isEmpty())
                    || projectArchitecture != null;
        }

        public boolean hasFeedback() {
            return feedbacks != null && !feedbacks.isEmpty();
        }

        public boolean hasReferences() {
            return references != null && !references.isEmpty();
        }
    }

    /**
     * 反馈项
     */
    private static class FeedbackItem {
        private String type;
        private String content;
        private String context;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
    }

    /**
     * 引用项
     */
    private static class ReferenceItem {
        private String name;
        private String type;
        private String location;
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
