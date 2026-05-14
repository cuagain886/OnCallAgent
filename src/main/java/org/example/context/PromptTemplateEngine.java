package org.example.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板引擎
 *
 * 负责加载、管理和渲染 Prompt 模板。
 * 支持从 YAML 配置文件加载模板。
 */
@Component
public class PromptTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateEngine.class);

    private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    @Value("${context.templates.enabled:true}")
    private boolean enabled;

    @Value("${context.templates.location:classpath:prompts/templates.yml}")
    private String templatesLocation;

    public PromptTemplateEngine(ObjectMapper objectMapper) {
        this.jsonMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @PostConstruct
    public void init() {
        if (enabled) {
            loadTemplates();
        }
    }

    /**
     * 加载模板
     */
    private void loadTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(templatesLocation);

            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String location = templatesLocation.toLowerCase();
                    ObjectMapper mapper = location.endsWith(".yml") || location.endsWith(".yaml")
                            ? yamlMapper : jsonMapper;
                    JsonNode root = mapper.readTree(is);
                    parseTemplates(root);
                    log.info("加载了 {} 个 Prompt 模板", templates.size());
                }
            } else {
                log.warn("模板配置文件不存在: {}", templatesLocation);
            }
        } catch (IOException e) {
            log.error("加载模板配置文件失败", e);
        }
    }

    /**
     * 解析模板配置
     */
    private void parseTemplates(JsonNode root) {
        JsonNode templatesNode = root.get("templates");
        if (templatesNode == null || !templatesNode.isObject()) {
            log.warn("模板配置格式错误: 缺少 templates 节点");
            return;
        }

        templatesNode.fields().forEachRemaining(entry -> {
            String templateId = entry.getKey();
            JsonNode templateNode = entry.getValue();

            try {
                PromptTemplate template = parseTemplate(templateId, templateNode);
                templates.put(templateId, template);
                log.debug("加载模板: {}", templateId);
            } catch (Exception e) {
                log.warn("解析模板失败: {}", templateId, e);
            }
        });
    }

    /**
     * 解析单个模板
     */
    private PromptTemplate parseTemplate(String id, JsonNode node) {
        String version = node.has("version") ? node.get("version").asText() : "1.0";
        String description = node.has("description") ? node.get("description").asText() : "";
        String template = node.has("template") ? node.get("template").asText() : "";

        List<PromptTemplate.TemplateVariable> variables = new ArrayList<>();
        if (node.has("variables") && node.get("variables").isArray()) {
            for (JsonNode varNode : node.get("variables")) {
                String name = varNode.get("name").asText();
                String type = varNode.has("type") ? varNode.get("type").asText() : "string";
                boolean required = varNode.has("required") && varNode.get("required").asBoolean();
                Object defaultValue = varNode.has("default") ? varNode.get("default").asText() : null;

                variables.add(new PromptTemplate.TemplateVariable(name, type, required, defaultValue));
            }
        }

        return new PromptTemplate(id, version, description, template, variables);
    }

    /**
     * 渲染模板
     *
     * @param templateId 模板 ID
     * @param context    变量上下文
     * @return 渲染后的内容
     */
    public String render(String templateId, Map<String, Object> context) {
        if (!enabled) {
            throw new IllegalStateException("Prompt 模板引擎未启用");
        }

        PromptTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }

        return template.render(context);
    }

    /**
     * 渲染模板（带默认值）
     */
    public String renderOrDefault(String templateId, Map<String, Object> context, String defaultValue) {
        if (!enabled) {
            return defaultValue;
        }

        PromptTemplate template = templates.get(templateId);
        if (template == null) {
            return defaultValue;
        }

        try {
            return template.render(context);
        } catch (Exception e) {
            log.warn("渲染模板失败: {}", templateId, e);
            return defaultValue;
        }
    }

    /**
     * 注册模板
     */
    public void register(PromptTemplate template) {
        templates.put(template.getId(), template);
    }

    /**
     * 获取模板
     */
    public PromptTemplate getTemplate(String templateId) {
        return templates.get(templateId);
    }

    /**
     * 获取所有模板 ID
     */
    public List<String> getTemplateIds() {
        return new ArrayList<>(templates.keySet());
    }

    /**
     * 检查模板是否存在
     */
    public boolean hasTemplate(String templateId) {
        return templates.containsKey(templateId);
    }

    /**
     * 获取模板数量
     */
    public int getTemplateCount() {
        return templates.size();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
