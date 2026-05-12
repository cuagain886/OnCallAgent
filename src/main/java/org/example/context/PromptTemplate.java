package org.example.context;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板
 *
 * 支持变量替换、条件渲染、循环渲染。
 */
public class PromptTemplate {

    private final String id;
    private final String version;
    private final String description;
    private final String template;
    private final List<TemplateVariable> variables;

    // 模板语法模式
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+(?:\\.\\w+)*)\\}\\}");
    private static final Pattern IF_PATTERN = Pattern.compile("\\{\\{#if\\s+(.+?)\\}\\}([\\s\\S]*?)\\{\\{/if\\}\\}");
    private static final Pattern EACH_PATTERN = Pattern.compile("\\{\\{#each\\s+(\\w+)\\}\\}([\\s\\S]*?)\\{\\{/each\\}\\}");

    public PromptTemplate(String id, String version, String description,
                           String template, List<TemplateVariable> variables) {
        this.id = id;
        this.version = version;
        this.description = description;
        this.template = template;
        this.variables = variables;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getTemplate() {
        return template;
    }

    public List<TemplateVariable> getVariables() {
        return variables;
    }

    /**
     * 渲染模板
     *
     * @param context 变量上下文
     * @return 渲染后的内容
     */
    public String render(Map<String, Object> context) {
        String result = template;

        // 1. 处理循环渲染
        result = processLoops(result, context);

        // 2. 处理条件渲染
        result = processConditionals(result, context);

        // 3. 变量替换
        result = replaceVariables(result, context);

        return result;
    }

    /**
     * 变量替换
     */
    private String replaceVariables(String template, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variablePath = matcher.group(1);
            Object value = getNestedValue(context, variablePath);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 处理条件渲染
     */
    private String processConditionals(String template, Map<String, Object> context) {
        Matcher matcher = IF_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            String content = matcher.group(2);

            if (evaluateCondition(condition, context)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(content));
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 处理循环渲染
     */
    private String processLoops(String template, Map<String, Object> context) {
        Matcher matcher = EACH_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String collectionName = matcher.group(1).trim();
            String itemTemplate = matcher.group(2);

            Object collection = context.get(collectionName);
            if (collection instanceof List<?> list) {
                StringBuilder items = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    String rendered = itemTemplate;

                    // 替换 {{this}} 为当前项
                    rendered = rendered.replace("{{this}}", item.toString());

                    // 如果是 Map，替换 {{this.xxx}}
                    if (item instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            rendered = rendered.replace("{{this." + entry.getKey() + "}}",
                                    entry.getValue() != null ? entry.getValue().toString() : "");
                        }
                    }

                    // 替换 {{@index}} 为当前索引
                    rendered = rendered.replace("{{@index}}", String.valueOf(i));

                    items.append(rendered);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(items.toString()));
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        // 处理简单的相等比较：variable == "value"
        if (condition.contains("==")) {
            String[] parts = condition.split("==");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim().replace("\"", "").replace("'", "");

                Object value = getNestedValue(context, left);
                return value != null && value.toString().equals(right);
            }
        }

        // 处理简单的不等比较：variable != "value"
        if (condition.contains("!=")) {
            String[] parts = condition.split("!=");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim().replace("\"", "").replace("'", "");

                Object value = getNestedValue(context, left);
                return value == null || !value.toString().equals(right);
            }
        }

        // 处理非空检查：variable
        Object value = getNestedValue(context, condition);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return !str.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return value != null;
    }

    /**
     * 获取嵌套属性值
     */
    private Object getNestedValue(Map<String, Object> context, String path) {
        String[] parts = path.split("\\.");
        Object current = context;

        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * 模板变量
     */
    public static class TemplateVariable {
        private final String name;
        private final String type;
        private final boolean required;
        private final Object defaultValue;

        public TemplateVariable(String name, String type, boolean required, Object defaultValue) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }
    }
}
