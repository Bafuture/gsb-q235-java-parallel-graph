package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class ParallelDfsTest {

    @Test
    void singleThreadDfsMatchesSequentialPreorder() {
        for (long seed : new long[]{1L, 2L, 42L, 99L}) {
            Graph graph = TestGraphs.randomGraph(5_000, 5, seed);
            try (ParallelTraverser traverser = new ParallelTraverser(1)) {
                TraversalResult result = traverser.dfs(graph, 0);
                assertThat(result.visitOrder())
                        .as("seed=" + seed)
                        .isEqualTo(TestGraphs.sequentialDfsPreorder(graph, 0));
            }
        }
    }

    @Test
    void parallelDfsHasDeterministicallyMergedOrderOnKnownGraph() {
        // 0 -> {1,2}, 1 -> 3, 2 -> 4; p = 4, owner(v) = v % 4.
        // Order is the merge by (round, worker id): round1 [0],
        // round2 worker1 [1], worker2 [2], round3 worker0 [4], worker3 [3].
        Graph graph = Graph.builder(5)
                .addEdge(0, 1).addEdge(0, 2)
                .addEdge(1, 3).addEdge(2, 4)
                .build();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            assertThat(traverser.dfs(graph, 0).visitOrder())
                    .containsExactly(0, 1, 2, 4, 3);
        }
    }

    @Test
    void parallelDfsVisitsExactlyReachableSetWithoutDuplicates() {
        Graph graph = TestGraphs.randomGraph(10_000, 6, 7L);
        HashSet<Integer> expected = new HashSet<>(
                TestGraphs.sequentialDfsPreorder(graph, 0));
        for (int p : new int[]{1, 2, 3, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                TraversalResult result = traverser.dfs(graph, 0);
                List<Integer> order = result.visitOrder();
                assertThat(order).doesNotHaveDuplicates();
                assertThat(new HashSet<>(order)).isEqualTo(expected);
                assertThat(order).startsWith(0);
            }
        }
    }

    @Test
    void handlesCyclesAndSelfLoops() {
        Graph graph = Graph.builder(5)
                .addEdge(0, 0).addEdge(0, 1).addEdge(1, 2)
                .addEdge(2, 0).addEdge(2, 3).addEdge(3, 4).addEdge(4, 1)
                .build();
        for (int p : new int[]{1, 4, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                TraversalResult result = traverser.dfs(graph, 0);
                assertThat(result.visitOrder())
                        .hasSize(5)
                        .doesNotHaveDuplicates()
                        .containsExactlyInAnyOrder(0, 1, 2, 3, 4);
            }
        }
    }

    @Test
    void traversesChainAndDiamond() {
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult chain = traverser.dfs(TestGraphs.chain(10_000), 0);
            assertThat(chain.visitOrder()).hasSize(10_000).doesNotHaveDuplicates();

            Graph diamond = Graph.builder(7)
                    .addEdge(0, 1).addEdge(0, 2)
                    .addEdge(1, 3).addEdge(1, 4)
                    .addEdge(2, 4).addEdge(2, 5)
                    .addEdge(3, 6).addEdge(4, 6).addEdge(5, 6)
                    .build();
            assertThat(traverser.dfs(diamond, 0).visitOrder())
                    .doesNotHaveDuplicates().hasSize(7);
        }
    }

    @Test
    void rejectsInvalidSource() {
        Graph graph = TestGraphs.chain(3);
        try (ParallelTraverser traverser = new ParallelTraverser(2)) {
            assertThatThrownBy(() -> traverser.dfs(graph, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
