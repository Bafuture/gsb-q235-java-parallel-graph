package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Requirement 8: parallel runs must agree with single-threaded runs. BFS is
 * identical including order; DFS agrees on the visited set at every
 * parallelism and on the exact order at parallelism 1.
 */
class ParallelVsSequentialTest {

    @Test
    void parallelBfsMatchesSingleThreadAndSequentialReference() {
        for (long seed : new long[]{3L, 17L, 123L}) {
            Graph graph = TestGraphs.randomGraph(8_000, 6, seed);
            List<List<Integer>> referenceLevels =
                    TestGraphs.sequentialBfsLevels(graph, 0);

            TraversalResult single;
            TraversalResult parallel;
            try (ParallelTraverser singleThread = new ParallelTraverser(1);
                 ParallelTraverser multiThread = new ParallelTraverser(8)) {
                single = singleThread.bfs(graph, 0);
                parallel = multiThread.bfs(graph, 0);
            }

            assertThat(parallel.levels()).isEqualTo(referenceLevels);
            assertThat(parallel.visitOrder()).isEqualTo(single.visitOrder());
            List<Integer> flattened = new ArrayList<>();
            referenceLevels.forEach(flattened::addAll);
            assertThat(parallel.visitOrder()).isEqualTo(flattened);
        }
    }

    @Test
    void parallelDfsMatchesSingleThread() {
        for (long seed : new long[]{5L, 55L, 555L}) {
            Graph graph = TestGraphs.randomGraph(8_000, 6, seed);
            List<Integer> reference = TestGraphs.sequentialDfsPreorder(graph, 0);

            TraversalResult single;
            TraversalResult parallel;
            try (ParallelTraverser singleThread = new ParallelTraverser(1);
                 ParallelTraverser multiThread = new ParallelTraverser(8)) {
                single = singleThread.dfs(graph, 0);
                parallel = multiThread.dfs(graph, 0);
            }

            // Parallelism 1 is byte-for-byte the sequential preorder.
            assertThat(single.visitOrder()).isEqualTo(reference);
            // Parallel DFS visits exactly the same nodes, exactly once.
            assertThat(parallel.visitOrder()).doesNotHaveDuplicates();
            assertThat(new HashSet<>(parallel.visitOrder()))
                    .isEqualTo(new HashSet<>(reference));
            assertThat(parallel.visitOrder()).hasSize(reference.size());
        }
    }
}
