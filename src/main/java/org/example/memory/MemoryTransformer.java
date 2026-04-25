package org.example.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 记忆转换器
 * 负责在短期记忆和长期记忆之间进行转换
 */
@Slf4j
@Service
public class MemoryTransformer {

    @Autowired
    private ShortTermMemoryManager shortTermMemoryManager;

    @Autowired
    private LongTermMemoryManager longTermMemoryManager;

    @Autowired
    private MemoryCompressor memoryCompressor;

    /**
     * 将短期记忆转换为长期记忆
     */
    public void transformToLongTerm(String sessionId) {
        List<Map<String, String>> history = shortTermMemoryManager.getHistory(sessionId);

        if (history.isEmpty()) {
            return;
        }

        // 压缩对话历史
        String compressed = memoryCompressor.compressHistory(history);

        if (compressed != null && !compressed.isBlank()) {
            // 压缩成功时，优先写入压缩结果而不是原始 history
            longTermMemoryManager.saveCompressedConversation(sessionId, compressed, history);
            log.info("已将压缩后的短期记忆转换为长期记忆: sessionId={}", sessionId);
            return;
        }

        // 未达到压缩阈值或压缩失败时，降级保存原始会话
        longTermMemoryManager.saveConversation(sessionId, history);
        log.info("压缩未命中，已按原始会话写入长期记忆: sessionId={}", sessionId);
    }

    /**
     * 从长期记忆检索并注入到短期记忆
     */
    public void injectFromLongTerm(String sessionId, String query) {
        // 检索相关记忆
        List<LongTermMemoryManager.Memory> memories =
                longTermMemoryManager.retrieveRelevantMemories(query, 3);

        if (memories.isEmpty()) {
            return;
        }

        // 将检索到的记忆作为系统消息注入
        StringBuilder context = new StringBuilder();
        context.append("【相关历史对话】\n");
        for (LongTermMemoryManager.Memory memory : memories) {
            context.append(memory.getContent()).append("\n\n");
        }
        context.append("【历史对话结束】\n");

        // 添加到短期记忆（作为系统消息）
        shortTermMemoryManager.addMessage(sessionId, "system", context.toString());

        log.info("已从长期记忆注入 {} 条相关记忆", memories.size());
    }
}