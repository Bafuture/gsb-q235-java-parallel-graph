package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ParallelBfsTest {

    private static final List<List<Integer>> EXPECTED_LEVELS = List.of(
            List.of(0),
            List.of(1, 2),
            List.of(3, 4, 5),
            List.of(6));

    private static Graph diamond() {
        //        0
        //       / \
        //      1   2
        //     / \ / \
        //    3   4   5
        //     \ / \ /
        //      6  (shared sinks on purpose)
        return Graph.builder(7)
                .addEdge(0, 1).addEdge(0, 2)
                .addEdge(1, 3).addEdge(1, 4)
                .addEdge(2, 4).addEdge(2, 5)
                .addEdge(3, 6).addEdge(4, 6).addEdge(5, 6)
                .build();
    }

    @Test
    void producesSortedLevelsAtEveryParallelism() {
        Graph graph = diamond();
        for (int p : new int[]{1, 2, 3, 4, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                TraversalResult result = traverser.bfs(graph, 0);

                assertThat(result.levels()).isEqualTo(EXPECTED_LEVELS);
                assertThat(result.visitOrder()).containsExactly(0, 1, 2, 3, 4, 5, 6);
                result.levels().forEach(level ->
                        assertThat(level).isSorted().doesNotHaveDuplicates());
            }
        }
    }

    @Test
    void handlesCycleWithoutRepetitionOrInfiniteLoop() {
        Graph graph = TestGraphs.cycle(50);
        for (int p : new int[]{1, 4, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                TraversalResult result = traverser.bfs(graph, 0);
                assertThat(result.visitOrder()).hasSize(50).doesNotHaveDuplicates();
                assertThat(result.levels()).hasSize(50);
            }
        }
    }

    @Test
    void handlesSelfLoop() {
        Graph graph = Graph.builder(3)
                .addEdge(0, 0).addEdge(0, 1)
                .addEdge(1, 1).addEdge(1, 2)
                .build();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult result = traverser.bfs(graph, 0);
            assertThat(result.levels()).isEqualTo(List.of(
                    List.of(0), List.of(1), List.of(2)));
            assertThat(result.visitOrder()).doesNotHaveDuplicates();
        }
    }

    @Test
    void visitsOnlyReachableComponent() {
        Graph graph = Graph.builder(6)
                .addEdge(0, 1).addEdge(1, 2)
                .addEdge(3, 4).addEdge(4, 5)
                .build();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult result = traverser.bfs(graph, 0);
            assertThat(result.visitOrder()).containsExactly(0, 1, 2);
        }
    }

    @Test
    void levelsAreSortedOnRandomGraphs() {
        Graph graph = TestGraphs.randomGraph(10_000, 6, 20261001L);
        for (int p : new int[]{1, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                TraversalResult result = traverser.bfs(graph, 42);
                for (List<Integer> level : result.levels()) {
                    assertThat(level).isSorted().doesNotHaveDuplicates();
                }
            }
        }
    }

    @Test
    void rejectsInvalidSourceAndParallelism() {
        Graph graph = TestGraphs.chain(3);
        assertThatThrownBy(() -> new ParallelTraverser(0))
                .isInstanceOf(IllegalArgumentException.class);
        try (ParallelTraverser traverser = new ParallelTraverser(2)) {
            assertThatThrownBy(() -> traverser.bfs(graph, 3))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> traverser.bfs(graph, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
