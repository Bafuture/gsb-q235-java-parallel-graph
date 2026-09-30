package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class DeterminismTest {

    @Test
    void bfsOrderIsStableAcrossRepeatedParallelRuns() {
        Graph graph = TestGraphs.randomGraph(30_000, 7, 11L);
        try (ParallelTraverser traverser = new ParallelTraverser(6)) {
            TraversalResult first = traverser.bfs(graph, 0);
            List<Integer> firstOrder = first.visitOrder();
            List<List<Integer>> firstLevels = first.levels();
            for (int run = 0; run < 10; run++) {
                TraversalResult result = traverser.bfs(graph, 0);
                assertThat(result.visitOrder()).isEqualTo(firstOrder);
                assertThat(result.levels()).isEqualTo(firstLevels);
            }
        }
    }

    @Test
    void dfsOrderIsStableAcrossRepeatedParallelRuns() {
        Graph graph = TestGraphs.randomGraph(30_000, 7, 13L);
        List<Integer> firstOrder;
        try (ParallelTraverser traverser = new ParallelTraverser(6)) {
            firstOrder = traverser.dfs(graph, 0).visitOrder();
            for (int run = 0; run < 10; run++) {
                TraversalResult result = traverser.dfs(graph, 0);
                assertThat(result.visitOrder()).isEqualTo(firstOrder);
                assertThat(result.visitOrder()).doesNotHaveDuplicates();
            }
        }
    }

    @Test
    void bfsOrderIsIdenticalForEveryParallelism() {
        Graph graph = TestGraphs.randomGraph(20_000, 6, 101L);
        List<Integer> reference;
        try (ParallelTraverser single = new ParallelTraverser(1)) {
            reference = single.bfs(graph, 0).visitOrder();
        }
        for (int p : new int[]{2, 3, 7, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                assertThat(traverser.bfs(graph, 0).visitOrder())
                        .as("parallelism=" + p)
                        .isEqualTo(reference);
            }
        }
    }

    @Test
    void dfsVisitedSetIsStableForEveryParallelism() {
        Graph graph = TestGraphs.randomGraph(20_000, 6, 202L);
        HashSet<Integer> reference;
        try (ParallelTraverser single = new ParallelTraverser(1)) {
            reference = new HashSet<>(single.dfs(graph, 0).visitOrder());
        }
        for (int p : new int[]{2, 3, 7, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                TraversalResult result = traverser.dfs(graph, 0);
                assertThat(result.visitOrder()).doesNotHaveDuplicates();
                assertThat(new HashSet<>(result.visitOrder()))
                        .as("parallelism=" + p)
                        .isEqualTo(reference);
            }
        }
    }

    @Test
    void everyNodeVisitedExactlyOnceUnderHighContention() {
        Graph graph = TestGraphs.denseBipartite(700);
        int reachable = 1 + 700 + 700;
        for (int run = 0; run < 5; run++) {
            try (ParallelTraverser traverser = new ParallelTraverser(8)) {
                TraversalResult bfs = traverser.bfs(graph, 0);
                assertThat(bfs.visitOrder()).hasSize(reachable);
                assertThat(new HashSet<>(bfs.visitOrder())).hasSize(reachable);
                assertThat(bfs.levels()).hasSize(3);

                TraversalResult dfs = traverser.dfs(graph, 0);
                assertThat(dfs.visitOrder()).hasSize(reachable);
                assertThat(new HashSet<>(dfs.visitOrder())).hasSize(reachable);
            }
        }
    }
}
