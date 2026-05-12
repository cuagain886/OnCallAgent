package org.example.context;

import java.util.Map;

/**
 * 上下文预算分配结果
 *
 * 包含每个上下文片段的 Token 预算分配。
 */
public class ContextAllocation {

    private final Map<ContextSection, Integer> allocation;
    private final int totalBudget;
    private final int usedTokens;

    public ContextAllocation(Map<ContextSection, Integer> allocation, int totalBudget) {
        this.allocation = allocation;
        this.totalBudget = totalBudget;
        this.usedTokens = allocation.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 获取指定片段的 Token 预算
     */
    public int getBudget(ContextSection section) {
        return allocation.getOrDefault(section, 0);
    }

    /**
     * 获取指定类型的 Token 预算
     */
    public int getBudgetByType(ContextSection.SectionType type) {
        return allocation.entrySet().stream()
                .filter(e -> e.getKey().getType() == type)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /**
     * 获取总预算
     */
    public int getTotalBudget() {
        return totalBudget;
    }

    /**
     * 获取已使用 Token 数
     */
    public int getUsedTokens() {
        return usedTokens;
    }

    /**
     * 获取剩余 Token 数
     */
    public int getRemainingTokens() {
        return totalBudget - usedTokens;
    }

    /**
     * 获取所有分配结果
     */
    public Map<ContextSection, Integer> getAllocation() {
        return allocation;
    }

    /**
     * 检查是否超限
     */
    public boolean isOverBudget() {
        return usedTokens > totalBudget;
    }

    /**
     * 获取使用率
     */
    public double getUsageRatio() {
        return (double) usedTokens / totalBudget;
    }

    @Override
    public String toString() {
        return String.format("ContextAllocation{total=%d, used=%d, remaining=%d, usage=%.1f%%}",
                totalBudget, usedTokens, getRemainingTokens(), getUsageRatio() * 100);
    }
}
