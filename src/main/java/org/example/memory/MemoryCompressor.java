package org.example.memory;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 记忆压缩器
 * 使用 LLM 对长对话进行总结和压缩
 */
@Slf4j
@Service
public class MemoryCompressor {

    @Autowired
    private DashScopeChatModel chatModel;

    @Value("${memory.compression.threshold:10}")
    private int compressionThreshold; // 超过多少轮对话后开始压缩

    /**
     * 压缩对话历史
     */
    public String compressHistory(List<Map<String, String>> history) {
        if (history.size() <= compressionThreshold * 2) {
            return null; // 不需要压缩
        }

        try {
            String conversation = formatConversation(history);

            String prompt = String.format(
                    "请用简洁的语言总结以下对话的核心内容，包括用户的问题和AI的回答要点：\n\n%s\n\n" +
                            "总结要求：\n" +
                            "1. 保留关键信息\n" +
                            "2. 突出用户意图\n" +
                            "3. 简明扼要\n" +
                            "4. 100字以内",
                    conversation
            );

            String summary = chatModel.call(prompt);
            log.info("对话历史已压缩，原始长度: {}, 压缩后长度: {}",
                    history.size(), summary.length());

            return summary;
        } catch (Exception e) {
            log.error("压缩对话历史失败", e);
            return null;
        }
    }

    /**
     * 格式化对话历史
     */
    private String formatConversation(List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("user".equals(role)) {
                sb.append("用户: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                sb.append("助手: ").append(content).append("\n");
            }
        }
        return sb.toString();
    }
}