package org.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 修复验证：buildSystemPrompt 分离系统指令与历史消息
 */
class ChatServiceTest {

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService();
    }

    // ==================== M1: 系统提示词历史分离 ====================

    @Test
    void buildSystemPrompt_noArgs_shouldNotContainHistory() {
        // 当 Hooks 启用时，调用无参版本，不应包含历史消息
        String prompt = chatService.buildSystemPrompt();

        // 应包含系统指令
        assertTrue(prompt.contains("智能助手"), "Should contain system instructions");
        assertTrue(prompt.contains("可用工具"), "Should contain tool descriptions");
        assertTrue(prompt.contains("严格规则"), "Should contain rules");

        // 不应包含历史消息标记
        assertFalse(prompt.contains("--- 对话历史 ---"), "Should NOT contain history section");
        assertFalse(prompt.contains("对话历史结束"), "Should NOT contain history end marker");
    }

    @Test
    void buildSystemPrompt_withEmptyHistory_shouldNotContainHistory() {
        // 空历史列表也不应包含历史消息标记
        String prompt = chatService.buildSystemPrompt(new ArrayList<>());

        assertFalse(prompt.contains("--- 对话历史 ---"), "Empty history should not produce history section");
    }

    @Test
    void buildSystemPrompt_withHistory_shouldContainHistory() {
        // 当 Hooks 关闭时，调用带历史版本，应包含历史消息
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "如何排查 CPU 问题？"),
                Map.of("role", "assistant", "content", "CPU 排查步骤如下...")
        );

        String prompt = chatService.buildSystemPrompt(history);

        // 应包含历史消息
        assertTrue(prompt.contains("--- 对话历史 ---"), "Should contain history section");
        assertTrue(prompt.contains("如何排查 CPU 问题"), "Should contain user message");
        assertTrue(prompt.contains("CPU 排查步骤如下"), "Should contain assistant message");
        assertTrue(prompt.contains("--- 对话历史结束 ---"), "Should contain history end marker");
    }

    @Test
    void buildSystemPrompt_withHistory_shouldContainSystemInstructions() {
        // 带历史版本也应包含系统指令
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "test"),
                Map.of("role", "assistant", "content", "response")
        );

        String prompt = chatService.buildSystemPrompt(history);

        assertTrue(prompt.contains("智能助手"), "Should contain system instructions");
        assertTrue(prompt.contains("可用工具"), "Should contain tool descriptions");
    }

    @Test
    void buildSystemPrompt_bothVersions_shouldContainSameSystemInstructions() {
        // 两个版本应包含相同的系统指令内容
        String promptWithoutHistory = chatService.buildSystemPrompt();
        String promptWithHistory = chatService.buildSystemPrompt(List.of(
                Map.of("role", "user", "content", "test"),
                Map.of("role", "assistant", "content", "response")
        ));

        // 验证核心系统指令在两个版本中都存在
        String[] coreInstructions = {"严格规则", "智能助手", "可用工具", "getCurrentDateTime",
                "queryInternalDocs", "QueryMetric", "使用建议"};
        for (String instruction : coreInstructions) {
            assertTrue(promptWithoutHistory.contains(instruction),
                    "No-history version should contain: " + instruction);
            assertTrue(promptWithHistory.contains(instruction),
                    "With-history version should contain: " + instruction);
        }
    }

    @Test
    void buildSystemPrompt_withHistory_shouldHaveCorrectOrdering() {
        // 验证历史消息的顺序：系统指令 → 历史 → 结束标记 → 末尾提示
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "第一条消息"),
                Map.of("role", "assistant", "content", "第一条回复"),
                Map.of("role", "user", "content", "第二条消息"),
                Map.of("role", "assistant", "content", "第二条回复")
        );

        String prompt = chatService.buildSystemPrompt(history);

        int systemIdx = prompt.indexOf("严格规则");
        int historyStartIdx = prompt.indexOf("--- 对话历史 ---");
        int firstMsgIdx = prompt.indexOf("第一条消息");
        int secondMsgIdx = prompt.indexOf("第二条消息");
        int historyEndIdx = prompt.indexOf("--- 对话历史结束 ---");

        assertTrue(systemIdx < historyStartIdx, "System instructions should come before history");
        assertTrue(historyStartIdx < firstMsgIdx, "History marker should come before messages");
        assertTrue(firstMsgIdx < secondMsgIdx, "Messages should be in chronological order");
        assertTrue(secondMsgIdx < historyEndIdx, "Messages should end before history end marker");
    }

    @Test
    void buildSystemPrompt_noArgs_shouldEndWithPrompt() {
        String prompt = chatService.buildSystemPrompt();
        assertTrue(prompt.contains("请基于以上对话历史"), "Should end with prompt instruction");
    }
}
