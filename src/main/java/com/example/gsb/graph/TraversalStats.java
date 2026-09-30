package com.example.gsb.graph;

import java.util.Arrays;

/**
 * Immutable statistics snapshot produced by a traversal run.
 *
 * <ul>
 *   <li>{@code nodeCount} / {@code edgeCount}: size of the input graph.</li>
 *   <li>{@code visitedCount}: number of nodes in the emitted visit order
 *       (equals the number of processed nodes; on cancellation this is the
 *       partial count).</li>
 *   <li>{@code elapsedNanos}: wall-clock time of the traversal.</li>
 *   <li>{@code nodesPerWorker}: distribution of processed nodes across the
 *       {@code parallelism} worker slots; slot {@code i} is executed by one
 *       pool thread per round.</li>
 * </ul>
 */
public final class TraversalStats {

    private final int nodeCount;
    private final long edgeCount;
    private final int visitedCount;
    private final long elapsedNanos;
    private final long[] nodesPerWorker;
    private final boolean cancelled;

    TraversalStats(int nodeCount, long edgeCount, int visitedCount,
                   long elapsedNanos, long[] nodesPerWorker, boolean cancelled) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.visitedCount = visitedCount;
        this.elapsedNanos = elapsedNanos;
        this.nodesPerWorker = nodesPerWorker.clone();
        this.cancelled = cancelled;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public long edgeCount() {
        return edgeCount;
    }

    public int visitedCount() {
        return visitedCount;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    /** Defensive copy of the per-worker processed-node distribution. */
    public long[] nodesPerWorker() {
        return nodesPerWorker.clone();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public String toString() {
        return "TraversalStats{nodes=" + nodeCount
                + ", edges=" + edgeCount
                + ", visited=" + visitedCount
                + ", elapsedMs=" + String.format("%.2f", elapsedMillis())
                + ", perWorker=" + Arrays.toString(nodesPerWorker)
                + ", cancelled=" + cancelled + '}';
    }
}
