package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Million-node smoke test (off by default to keep the default test suite
 * lean; enable with -Dlarge.tests=true).
 */
@EnabledIfSystemProperty(named = "large.tests", matches = "true")
class LargeGraphTest {

    @Test
    void traversesMillionNodeGraphInParallel() {
        Graph graph = TestGraphs.randomGraph(1_000_000, 4, 20261001L);
        assertThat(graph.nodeCount()).isEqualTo(1_000_000);

        try (ParallelTraverser single = new ParallelTraverser(1);
             ParallelTraverser parallel = new ParallelTraverser(8)) {
            long t0 = System.nanoTime();
            TraversalResult singleBfs = single.bfs(graph, 0);
            long singleMs = (System.nanoTime() - t0) / 1_000_000;

            t0 = System.nanoTime();
            TraversalResult parallelBfs = parallel.bfs(graph, 0);
            long parallelMs = (System.nanoTime() - t0) / 1_000_000;

            assertThat(parallelBfs.visitOrder())
                    .doesNotHaveDuplicates()
                    .isEqualTo(singleBfs.visitOrder());

            TraversalResult dfs1 = parallel.dfs(graph, 0);
            TraversalResult dfs2 = parallel.dfs(graph, 0);
            assertThat(dfs2.visitOrder()).isEqualTo(dfs1.visitOrder());
            assertThat(dfs1.visitOrder()).doesNotHaveDuplicates();
            assertThat(dfs1.visitOrder()).hasSize(singleBfs.visitOrder().size());

            System.out.println("BFS single=" + singleMs + "ms parallel=" + parallelMs + "ms, "
                    + parallelBfs.stats());
        }
    }
}
