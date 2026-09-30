package com.example.gsb.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable outcome of a traversal.
 *
 * <p>{@link #visitOrder()} is the deterministic visit sequence (see
 * {@link ParallelTraverser} for the ordering guarantees). For BFS,
 * {@link #levels()} additionally exposes the nodes grouped by BFS depth with
 * each level sorted ascending; for DFS it is empty.
 *
 * <p>When the traversal was cancelled, the result contains the partial
 * output produced up to the cancellation point and
 * {@link #isCancelled()} is {@code true}.
 */
public final class TraversalResult {

    private final List<Integer> visitOrder;
    private final List<List<Integer>> levels;
    private final TraversalStats stats;

    TraversalResult(List<Integer> visitOrder, List<List<Integer>> levels,
                    TraversalStats stats) {
        this.visitOrder = Collections.unmodifiableList(new ArrayList<>(visitOrder));
        List<List<Integer>> levelsCopy = new ArrayList<>(levels.size());
        for (List<Integer> level : levels) {
            levelsCopy.add(Collections.unmodifiableList(new ArrayList<>(level)));
        }
        this.levels = Collections.unmodifiableList(levelsCopy);
        this.stats = stats;
    }

    public List<Integer> visitOrder() {
        return visitOrder;
    }

    /** BFS levels (each sorted ascending); empty for DFS results. */
    public List<List<Integer>> levels() {
        return levels;
    }

    public TraversalStats stats() {
        return stats;
    }

    public boolean isCancelled() {
        return stats.isCancelled();
    }
}
