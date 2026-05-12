package org.example.memory.markdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Markdown 记忆召回管理器
 *
 * 负责对话开始时的异步 Prefetch 流程：
 * 1. 扫描记忆目录下所有 .md 文件的 frontmatter
 * 2. 使用 LLM 做智能筛选
 * 3. 最多选择 5 个最相关的记忆文件
 * 4. 完整加载选中文件内容
 */
@Component
public class MarkdownMemoryRecallManager {

    private static final Logger log = LoggerFactory.getLogger(MarkdownMemoryRecallManager.class);

    private static final int MAX_SELECTED_MEMORIES = 5;

    private final MarkdownMemoryManager memoryManager;
    private final FrontmatterScanner scanner;
    private final ChatModel chatModel;

    @Value("${memory.markdown.recall.max-context-length:3000}")
    private int maxContextLength;

    public MarkdownMemoryRecallManager(MarkdownMemoryManager memoryManager,
                                        FrontmatterScanner scanner,
                                        ChatModel chatModel) {
        this.memoryManager = memoryManager;
        this.scanner = scanner;
        this.chatModel = chatModel;
    }

    /**
     * 异步召回记忆
     *
     * @param query 用户查询
     * @return 异步结果，包含选中的记忆列表
     */
    public CompletableFuture<List<RecalledMemory>> recallAsync(String query) {
        return CompletableFuture.supplyAsync(() -> recall(query));
    }

    /**
     * 同步召回记忆
     *
     * @param query 用户查询
     * @return 选中的记忆列表
     */
    public List<RecalledMemory> recall(String query) {
        if (!memoryManager.isEnabled()) {
            return Collections.emptyList();
        }

        try {
            // Step 1: 扫描所有记忆文件的 frontmatter
            Path memoryRoot = memoryManager.getMemoryRoot();
            List<FrontmatterScanner.ScannedMemory> allMemories = scanner.scanAll(memoryRoot);

            if (allMemories.isEmpty()) {
                log.debug("No memory files found");
                return Collections.emptyList();
            }

            // Step 2: 使用 LLM 智能筛选
            List<FrontmatterScanner.ScannedMemory> selected = intelligentFilter(allMemories, query);

            // Step 3: 完整加载选中文件
            List<RecalledMemory> result = selected.stream()
                    .map(this::loadFullContent)
                    .filter(m -> m != null)
                    .limit(MAX_SELECTED_MEMORIES)
                    .toList();

            log.info("Recalled {} memories for query: {}", result.size(), query);
            return result;
        } catch (Exception e) {
            log.error("Failed to recall memories", e);
            return Collections.emptyList();
        }
    }

    /**
     * 使用 LLM 进行智能筛选
     */
    private List<FrontmatterScanner.ScannedMemory> intelligentFilter(
            List<FrontmatterScanner.ScannedMemory> candidates, String query) {

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 如果候选数量不超过限制，直接返回
        if (candidates.size() <= MAX_SELECTED_MEMORIES) {
            return candidates;
        }

        try {
            String prompt = buildFilterPrompt(candidates, query);
            String response = chatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            List<Integer> selectedIndices = parseSelectedIndices(response);
            return selectedIndices.stream()
                    .filter(i -> i >= 0 && i < candidates.size())
                    .map(candidates::get)
                    .toList();
        } catch (Exception e) {
            log.warn("LLM filter failed, falling back to first {} memories", MAX_SELECTED_MEMORIES, e);
            return candidates.subList(0, MAX_SELECTED_MEMORIES);
        }
    }

    /**
     * 构建筛选提示词
     */
    private String buildFilterPrompt(List<FrontmatterScanner.ScannedMemory> candidates, String query) {
        StringBuilder candidatesText = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            FrontmatterScanner.ScannedMemory mem = candidates.get(i);
            candidatesText.append(String.format("[%d] %s (%s): %s\n",
                    i, mem.getName(), mem.getType().getDirectory(), mem.getDescription()));
        }

        return String.format("""
                你是一个记忆检索助手。根据用户的当前查询，从候选记忆列表中选择最多%d个最相关的记忆。

                用户查询：%s

                候选记忆：
                %s

                请返回选中的记忆索引（JSON数组），例如：[0, 2, 4]
                选择标准：
                1. 与当前查询的语义相关性
                2. 记忆的类型是否匹配查询场景
                3. 记忆的描述是否包含关键信息

                只返回JSON数组，不要有其他内容。
                """, MAX_SELECTED_MEMORIES, query, candidatesText);
    }

    /**
     * 解析 LLM 返回的索引数组
     */
    private List<Integer> parseSelectedIndices(String response) {
        try {
            // 提取 JSON 数组部分
            String json = response.trim();
            int start = json.indexOf('[');
            int end = json.indexOf(']');

            if (start == -1 || end == -1 || start >= end) {
                log.warn("Invalid response format: {}", response);
                return Collections.emptyList();
            }

            String arrayContent = json.substring(start + 1, end);
            return java.util.Arrays.stream(arrayContent.split(","))
                    .map(String::trim)
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    })
                    .filter(i -> i >= 0)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse selected indices: {}", response, e);
            return Collections.emptyList();
        }
    }

    /**
     * 加载记忆文件的完整内容
     */
    private RecalledMemory loadFullContent(FrontmatterScanner.ScannedMemory scanned) {
        try {
            String content = memoryManager.readMemory(scanned.getType(),
                    scanned.getRelativePath().toString().replace(".md", ""));

            if (content == null || content.isEmpty()) {
                return null;
            }

            return new RecalledMemory(
                    scanned.getName(),
                    scanned.getDescription(),
                    scanned.getType(),
                    content
            );
        } catch (Exception e) {
            log.warn("Failed to load memory content: {}", scanned.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 构建召回上下文字符串
     *
     * @param memories 召回的记忆列表
     * @return 格式化的上下文字符串
     */
    public String buildRecallContext(List<RecalledMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【相关记忆】\n");

        int totalLength = 0;
        for (RecalledMemory memory : memories) {
            String memoryText = String.format("\n## %s (%s)\n%s\n",
                    memory.getName(),
                    memory.getType().getDescription(),
                    memory.getContent());

            // 检查长度限制
            if (totalLength + memoryText.length() > maxContextLength) {
                log.debug("Reached max context length, truncating");
                break;
            }

            sb.append(memoryText);
            totalLength += memoryText.length();
        }

        sb.append("\n【记忆结束】\n");
        return sb.toString();
    }

    /**
     * 召回的记忆内部类
     */
    public static class RecalledMemory {
        private final String name;
        private final String description;
        private final MemoryType type;
        private final String content;

        public RecalledMemory(String name, String description, MemoryType type, String content) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.content = content;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public MemoryType getType() {
            return type;
        }

        public String getContent() {
            return content;
        }
    }
}
