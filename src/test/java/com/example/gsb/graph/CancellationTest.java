package com.example.gsb.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationTest {

    private static Graph chain(int n) {
        return Graphs.chain(n);
    }

    @Test
    void bfsCanBeCancelledAndPartialResultRemainsUsable() {
        int n = 500_000;
        TraversalHandle h = ParallelGraphTraverser.run(Graphs.chain(n), 0, 4, TraversalKind.BFS);
        h.cancel();
        TraversalResult result = h.result();
        assertThat(h.isDone()).isTrue();
        assertThat(result.cancelled()).isTrue();
        assertThat(result.order()).doesNotHaveDuplicates();
        for (List<Integer> level : result.levels()) {
            assertThat(level).doesNotHaveDuplicates();
        }
    }

    @Test
    void dfsCanBeCancelledAndPartialResultRemainsUsable() {
        int n = 200_000;
        TraversalHandle h = ParallelGraphTraverser.run(Graphs.chain(n), 0, 4, TraversalKind.DFS);
        h.cancel();
        TraversalResult result = h.result();
        assertThat(h.isDone()).isTrue();
        assertThat(result.cancelled()).isTrue();
        assertThat(result.order()).doesNotHaveDuplicates();
        // 取消后 worker 必须退出：短时间内句柄已结束即证明线程不再阻塞（线程为守护线程）。
        assertThat(h.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancelMidTraversalThenWorkersTerminateForCyclicGraph() {
        Graph g = Graphs.random(100_000, 400_000, 9L);
        TraversalHandle h = ParallelGraphTraverser.run(g, 0, 6, TraversalKind.DFS);
        h.cancel();
        TraversalResult r = h.result();
        assertThat(r.cancelled()).isTrue();
        assertThat(r.order()).doesNotHaveDuplicates();
    }

    @Test
    void cancelIsIdempotent() {
        TraversalHandle h = ParallelGraphTraverser.run(Graphs.chain(100), 0, 2, TraversalKind.BFS);
        h.cancel();
        h.cancel();
        assertThat(h.result().cancelled()).isTrue();
    }
}
