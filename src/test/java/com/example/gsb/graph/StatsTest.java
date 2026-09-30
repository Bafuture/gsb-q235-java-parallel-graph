package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class StatsTest {

    @Test
    void reportsGraphSizeTimingAndVisitedCount() {
        Graph graph = Graph.builder(4)
                .addEdge(0, 1).addEdge(0, 2).addEdge(0, 2)
                .addEdge(1, 3).addEdge(2, 3)
                .build();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult result = traverser.bfs(graph, 0);
            TraversalStats stats = result.stats();

            assertThat(stats.nodeCount()).isEqualTo(4);
            assertThat(stats.edgeCount()).isEqualTo(4); // duplicate edge merged
            assertThat(stats.visitedCount()).isEqualTo(4);
            assertThat(stats.elapsedNanos()).isPositive();
            assertThat(stats.elapsedMillis()).isPositive();
            assertThat(stats.isCancelled()).isFalse();
            assertThat(stats.toString()).contains("visited=4");
        }
    }

    @Test
    void perWorkerDistributionSumsToVisitedCount() {
        // Source fans out to 1000 level-1 nodes: every worker must expand a
        // non-empty contiguous chunk.
        Graph.Builder builder = Graph.builder(1001);
        for (int i = 1; i <= 1000; i++) {
            builder.addEdge(0, i);
        }
        Graph graph = builder.build();

        for (int p : new int[]{1, 4, 8}) {
            try (ParallelTraverser traverser = new ParallelTraverser(p)) {
                TraversalResult result = traverser.bfs(graph, 0);
                long[] perWorker = result.stats().nodesPerWorker();
                assertThat(perWorker).hasSize(p);
                assertThat(Arrays.stream(perWorker).sum())
                        .isEqualTo(result.stats().visitedCount());
                for (int w = 0; w < p; w++) {
                    assertThat(perWorker[w]).as("worker " + w).isPositive();
                }
            }
        }
    }

    @Test
    void dfsDistributionSumsToVisitedCount() {
        // 0 -> {1,2,3,4} so with p=4 every owner slot gets work.
        Graph graph = Graph.builder(5)
                .addEdge(0, 1).addEdge(0, 2).addEdge(0, 3).addEdge(0, 4)
                .build();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult result = traverser.dfs(graph, 0);
            long[] perWorker = result.stats().nodesPerWorker();
            assertThat(perWorker).hasSize(4);
            assertThat(Arrays.stream(perWorker).sum())
                    .isEqualTo(5);
            for (long count : perWorker) {
                assertThat(count).isPositive();
            }
        }
    }

    @Test
    void statsAreRecordedForCancelledRun() {
        Graph graph = TestGraphs.randomGraph(50_000, 6, 9L);
        CancellationToken token = new CancellationToken();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult result = traverser.bfs(graph, 0, token,
                    new java.util.function.IntConsumer() {
                        private int count;

                        @Override
                        public void accept(int node) {
                            if (++count >= 100) {
                                token.cancel();
                            }
                        }
                    });
            assertThat(result.isCancelled()).isTrue();
            assertThat(result.stats().visitedCount()).isEqualTo(result.visitOrder().size());
            assertThat(result.stats().nodeCount()).isEqualTo(50_000);
        }
    }
}
