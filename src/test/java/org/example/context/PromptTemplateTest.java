package org.example.context;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptTemplate 单元测试
 */
class PromptTemplateTest {

    @Test
    void testSimpleVariableReplacement() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "你好，{{name}}！",
                List.of()
        );

        Map<String, Object> context = Map.of("name", "张三");

        // When
        String result = template.render(context);

        // Then
        assertEquals("你好，张三！", result);
    }

    @Test
    void testMultipleVariableReplacement() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "{{greeting}}，{{name}}！今天是{{day}}。",
                List.of()
        );

        Map<String, Object> context = Map.of(
                "greeting", "你好",
                "name", "张三",
                "day", "星期一"
        );

        // When
        String result = template.render(context);

        // Then
        assertEquals("你好，张三！今天是星期一。", result);
    }

    @Test
    void testConditionalRendering() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "{{#if showWelcome}}欢迎！{{/if}}{{#if showFarewell}}再见！{{/if}}",
                List.of()
        );

        // When - 只显示欢迎
        Map<String, Object> context1 = Map.of("showWelcome", true, "showFarewell", false);
        String result1 = template.render(context1);
        assertEquals("欢迎！", result1);

        // When - 只显示再见
        Map<String, Object> context2 = Map.of("showWelcome", false, "showFarewell", true);
        String result2 = template.render(context2);
        assertEquals("再见！", result2);
    }

    @Test
    void testConditionalWithEquality() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "{{#if role == \"admin\"}}管理员{{/if}}{{#if role == \"user\"}}普通用户{{/if}}",
                List.of()
        );

        // When - 管理员
        Map<String, Object> context1 = Map.of("role", "admin");
        String result1 = template.render(context1);
        assertEquals("管理员", result1);

        // When - 普通用户
        Map<String, Object> context2 = Map.of("role", "user");
        String result2 = template.render(context2);
        assertEquals("普通用户", result2);
    }

    @Test
    void testLoopRendering() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "列表：\n{{#each items}}\n- {{this}}\n{{/each}}",
                List.of()
        );

        Map<String, Object> context = Map.of("items", List.of("苹果", "香蕉", "橙子"));

        // When
        String result = template.render(context);

        // Then
        assertEquals("列表：\n\n- 苹果\n\n- 香蕉\n\n- 橙子\n", result);
    }

    @Test
    void testLoopWithMapItems() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "{{#each users}}\n- {{this.name}} ({{this.role}})\n{{/each}}",
                List.of()
        );

        Map<String, Object> context = Map.of("users", List.of(
                Map.of("name", "张三", "role", "admin"),
                Map.of("name", "李四", "role", "user")
        ));

        // When
        String result = template.render(context);

        // Then
        assertTrue(result.contains("- 张三 (admin)"));
        assertTrue(result.contains("- 李四 (user)"));
    }

    @Test
    void testNestedVariablePath() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "用户：{{user.name}}，角色：{{user.role}}",
                List.of()
        );

        Map<String, Object> context = Map.of(
                "user", Map.of("name", "张三", "role", "admin")
        );

        // When
        String result = template.render(context);

        // Then
        assertEquals("用户：张三，角色：admin", result);
    }

    @Test
    void testMissingVariable() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                "你好，{{name}}！",
                List.of()
        );

        Map<String, Object> context = new HashMap<>();

        // When
        String result = template.render(context);

        // Then
        assertEquals("你好，！", result);
    }

    @Test
    void testComplexTemplate() {
        // Given
        PromptTemplate template = new PromptTemplate(
                "test", "1.0", "测试模板",
                """
                你是{{role}}。

                {{#if showRules}}
                ## 规则
                {{#each rules}}
                {{@index}}. {{this}}
                {{/each}}
                {{/if}}

                {{#if context}}
                ## 上下文
                {{context}}
                {{/if}}
                """,
                List.of()
        );

        Map<String, Object> context = Map.of(
                "role", "AI 助手",
                "showRules", true,
                "rules", List.of("保持专业", "回答简洁", "避免猜测"),
                "context", "这是一个测试场景"
        );

        // When
        String result = template.render(context);

        // Then
        assertTrue(result.contains("你是AI 助手"));
        assertTrue(result.contains("0. 保持专业"));
        assertTrue(result.contains("1. 回答简洁"));
        assertTrue(result.contains("2. 避免猜测"));
        assertTrue(result.contains("这是一个测试场景"));
    }

    @Test
    void testTemplateVariable() {
        // Given
        PromptTemplate.TemplateVariable variable = new PromptTemplate.TemplateVariable(
                "name", "string", true, null
        );

        // Then
        assertEquals("name", variable.getName());
        assertEquals("string", variable.getType());
        assertTrue(variable.isRequired());
        assertNull(variable.getDefaultValue());
    }
}
