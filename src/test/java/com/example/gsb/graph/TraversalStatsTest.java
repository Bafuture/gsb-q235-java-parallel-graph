package com.example.gsb.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraversalStatsTest {

    @Test
    void statsReportGraphSizeTimingAndPerThreadDistribution() {
        Graph g = Graphs.grid(100, 100);
        TraversalResult r = ParallelGraphTraverser.bfs(g, 0, 4);
        TraversalStats s = r.stats();

        assertThat(s.vertexCount()).isEqualTo(10_000);
        assertThat(s.edgeCount()).isEqualTo(g.edgeCount());
        assertThat(s.visitedCount()).isEqualTo(10_000);
        assertThat(s.elapsedNanos()).isGreaterThanOrEqualTo(0);
        assertThat(s.elapsedMillis()).isGreaterThanOrEqualTo(0);

        Map<String, Long> dist = s.nodesPerThread();
        assertThat(dist).isNotEmpty();
        long sum = dist.values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(s.visitedCount());
    }

    @Test
    void dfsPerThreadCountsSumToVisited() {
        Graph g = Graphs.cyclicDense(300, 5L);
        TraversalResult r = ParallelGraphTraverser.dfs(g, 0, 5);
        long sum = r.stats().nodesPerThread().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(r.stats().visitedCount());
        assertThat(r.stats().visitedCount()).isEqualTo(r.order().size());
    }

    @Test
    void singleThreadedRunHasSingleThreadInDistribution() {
        Graph g = Graphs.random(1000, 3000, 1L);
        TraversalResult r = ParallelGraphTraverser.bfs(g, 0, 1);
        // 并行度 1：没有 worker 池，全部由编排线程完成。
        assertThat(r.stats().nodesPerThread()).containsKey("pgt-orchestrator");
        assertThat(r.stats().nodesPerThread().get("pgt-orchestrator")).isEqualTo(r.stats().visitedCount());

        TraversalResult d = ParallelGraphTraverser.dfs(g, 0, 1);
        assertThat(d.stats().nodesPerThread()).hasSize(1);
        assertThat(d.stats().nodesPerThread().get("pgt-dfs-worker-0")).isEqualTo(d.stats().visitedCount());
    }
}
