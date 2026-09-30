package com.example.gsb.graph;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一次遍历的统计信息：图规模、耗时、各线程处理（visit）的节点数分布。
 */
public final class TraversalStats {

    private final int vertexCount;
    private final long edgeCount;
    private final long visitedCount;
    private final long elapsedNanos;
    private final Map<String, Long> nodesPerThread;

    public TraversalStats(int vertexCount, long edgeCount, long visitedCount,
                          long elapsedNanos, Map<String, Long> nodesPerThread) {
        this.vertexCount = vertexCount;
        this.edgeCount = edgeCount;
        this.visitedCount = visitedCount;
        this.elapsedNanos = elapsedNanos;
        this.nodesPerThread = Collections.unmodifiableMap(new TreeMap<>(nodesPerThread));
    }

    public int vertexCount() {
        return vertexCount;
    }

    public long edgeCount() {
        return edgeCount;
    }

    /** 实际被 visit 的节点数（去重后）。 */
    public long visitedCount() {
        return visitedCount;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }

    /** 线程名 -> 该线程处理的节点数。只包含处理过节点的线程。 */
    public Map<String, Long> nodesPerThread() {
        return nodesPerThread;
    }

    @Override
    public String toString() {
        return "TraversalStats{vertices=" + vertexCount
                + ", edges=" + edgeCount
                + ", visited=" + visitedCount
                + ", elapsedMs=" + elapsedMillis()
                + ", nodesPerThread=" + nodesPerThread + '}';
    }
}
