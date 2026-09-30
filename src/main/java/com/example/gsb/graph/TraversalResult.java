package com.example.gsb.graph;

import java.util.Collections;
import java.util.List;

/**
 * 遍历结果。order 为节点访问顺序（确定性，见 README）；BFS 额外提供按层视图。
 * 取消后返回的是已处理部分的结果，cancelled 为 true。
 */
public final class TraversalResult {

    private final TraversalKind kind;
    private final List<Integer> order;
    private final List<List<Integer>> levels;
    private final boolean cancelled;
    private final TraversalStats stats;

    public TraversalResult(TraversalKind kind, List<Integer> order, List<List<Integer>> levels,
                           boolean cancelled, TraversalStats stats) {
        this.kind = kind;
        this.order = Collections.unmodifiableList(order);
        this.levels = levels == null ? null : Collections.unmodifiableList(levels);
        this.cancelled = cancelled;
        this.stats = stats;
    }

    public TraversalKind kind() {
        return kind;
    }

    /** 节点访问顺序。 */
    public List<Integer> order() {
        return order;
    }

    /** BFS 按层输出：levels().get(i) 为第 i 层节点集合（层内按 id 升序）。DFS 不支持，返回 null。 */
    public List<List<Integer>> levels() {
        return levels;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public TraversalStats stats() {
        return stats;
    }
}
