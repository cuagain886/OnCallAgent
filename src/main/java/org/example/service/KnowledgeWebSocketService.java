package org.example.service;

import org.example.model.knowledge.MaintenanceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Phase 3: 知识库维护 WebSocket 推送服务
 * 当任务状态变化时通知前端
 */
@Service
public class KnowledgeWebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeWebSocketService.class);

    // 已注册的监听器（前端连接时注册）
    private final Set<Consumer<String>> listeners = ConcurrentHashMap.newKeySet();

    /**
     * 注册状态变化监听器
     */
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    /**
     * 移除监听器
     */
    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /**
     * 推送任务状态更新
     */
    public void notifyTaskUpdate(MaintenanceTask task) {
        String message = String.format(
                "{\"type\":\"TASK_UPDATE\",\"taskId\":\"%s\",\"status\":\"%s\",\"currentAgent\":\"%s\"}",
                task.getTaskId(),
                task.getStatus().name(),
                task.getCurrentAgent() != null ? task.getCurrentAgent() : ""
        );

        logger.debug("推送任务状态更新: {}", message);
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(message);
            } catch (Exception e) {
                logger.warn("推送消息失败", e);
            }
        }
    }

    /**
     * 推送审核请求通知
     */
    public void notifyReviewRequired(MaintenanceTask task) {
        String message = String.format(
                "{\"type\":\"REVIEW_REQUIRED\",\"taskId\":\"%s\",\"filename\":\"%s\"}",
                task.getTaskId(),
                task.getGeneratedFilename() != null ? task.getGeneratedFilename() : ""
        );

        logger.info("推送审核请求: taskId={}", task.getTaskId());
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(message);
            } catch (Exception e) {
                logger.warn("推送审核通知失败", e);
            }
        }
    }

    /**
     * 推送任务完成通知
     */
    public void notifyTaskCompleted(MaintenanceTask task) {
        String message = String.format(
                "{\"type\":\"TASK_COMPLETED\",\"taskId\":\"%s\",\"status\":\"%s\",\"filename\":\"%s\"}",
                task.getTaskId(),
                task.getStatus().name(),
                task.getGeneratedFilename() != null ? task.getGeneratedFilename() : ""
        );

        logger.info("推送任务完成: taskId={}", task.getTaskId());
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(message);
            } catch (Exception e) {
                logger.warn("推送完成通知失败", e);
            }
        }
    }

    public int getListenerCount() {
        return listeners.size();
    }
}
