package com.example.gsb.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 并行与单线程结果一致、去重、确定性（多次运行）。 */
class TraversalOrderTest {

    private List<Graph> fixtures() {
        List<Graph> graphs = new ArrayList<>();
        graphs.add(Graphs.withSelfLoopsAndDuplicates());
        graphs.add(Graphs.grid(10, 12));
        graphs.add(Graphs.chain(500));
        graphs.add(Graphs.cyclicDense(50, 1L));
        graphs.add(Graphs.cyclicDense(200, 2L));
        for (long seed = 100; seed < 112; seed++) {
            graphs.add(Graphs.random(300, 900, seed));
            graphs.add(Graphs.random(80, 1600, seed)); // 近似稠密、环多
            graphs.add(Graphs.random(1000, 1500, seed)); // 稀疏
        }
        return graphs;
    }

    @Test
    void bfsParallelMatchesReferenceAndIsDeterministic() {
        for (Graph g : fixtures()) {
            List<Integer> expected = ReferenceTraversal.bfs(g, 0);
            for (int p : new int[]{1, 2, 3, 4, 8}) {
                List<Integer> first = null;
                for (int run = 0; run < 3; run++) {
                    TraversalResult r = ParallelGraphTraverser.bfs(g, 0, p);
                    assertThat(r.cancelled()).isFalse();
                    assertNoDuplicates(r.order());
                    assertThat(r.order()).as("graph=%s p=%d", g, p).isEqualTo(expected);
                    if (first == null) {
                        first = r.order();
                    } else {
                        assertThat(r.order()).isEqualTo(first);
                    }
                }
            }
        }
    }

    @Test
    void dfsParallelMatchesReferenceAndIsDeterministic() {
        for (Graph g : fixtures()) {
            List<Integer> expected = ReferenceTraversal.dfs(g, 0);
            for (int p : new int[]{1, 2, 3, 4, 8}) {
                List<Integer> first = null;
                for (int run = 0; run < 3; run++) {
                    TraversalResult r = ParallelGraphTraverser.dfs(g, 0, p);
                    assertThat(r.cancelled()).isFalse();
                    assertNoDuplicates(r.order());
                    assertThat(r.order()).as("graph vertices=%d edges=%d p=%d run=%d",
                            g.vertexCount(), g.edgeCount(), p, run).isEqualTo(expected);
                    if (first == null) {
                        first = r.order();
                    } else {
                        assertThat(r.order()).isEqualTo(first);
                    }
                }
            }
        }
    }

    @Test
    void selfLoopsAndCyclesDoNotDuplicateOrHang() {
        Graph g = Graphs.withSelfLoopsAndDuplicates();
        TraversalResult bfs = ParallelGraphTraverser.bfs(g, 0, 4);
        TraversalResult dfs = ParallelGraphTraverser.dfs(g, 0, 4);
        assertThat(new HashSet<>(bfs.order())).containsExactlyInAnyOrderElementsOf(bfs.order());
        assertThat(new HashSet<>(dfs.order())).containsExactlyInAnyOrderElementsOf(dfs.order());
        assertThat(bfs.order()).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(dfs.order()).isEqualTo(ReferenceTraversal.dfs(g, 0));
    }

    @Test
    void largeCyclicGraphStaysCorrect() {
        Graph g = Graphs.random(20_000, 120_000, 7L);
        TraversalResult dfs = ParallelGraphTraverser.dfs(g, 0, 6);
        assertNoDuplicates(dfs.order());
        assertThat(dfs.order()).isEqualTo(ReferenceTraversal.dfs(g, 0));
        TraversalResult bfs = ParallelGraphTraverser.bfs(g, 0, 6);
        assertNoDuplicates(bfs.order());
        assertThat(bfs.order()).isEqualTo(ReferenceTraversal.bfs(g, 0));
    }

    private void assertNoDuplicates(List<Integer> order) {
        Set<Integer> unique = new HashSet<>(order);
        assertThat(unique).hasSize(order.size());
    }
}
