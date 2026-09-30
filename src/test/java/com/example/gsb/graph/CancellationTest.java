package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CancellationTest {

    @Test
    void preCancelledBfsReturnsEmptyUsableResultImmediately() {
        Graph graph = TestGraphs.randomGraph(10_000, 6, 1L);
        CancellationToken token = new CancellationToken();
        token.cancel();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult result = traverser.bfs(graph, 0, token);
            assertThat(result.isCancelled()).isTrue();
            assertThat(result.visitOrder()).isEmpty();
            assertThat(result.levels()).isEmpty();
            assertThat(result.stats().visitedCount()).isZero();
        }
    }

    @Test
    void preCancelledDfsReturnsEmptyUsableResultImmediately() {
        Graph graph = TestGraphs.randomGraph(10_000, 6, 2L);
        CancellationToken token = new CancellationToken();
        token.cancel();
        try (ParallelTraverser traverser = new ParallelTraverser(4)) {
            TraversalResult result = traverser.dfs(graph, 0, token);
            assertThat(result.isCancelled()).isTrue();
            assertThat(result.visitOrder()).isEmpty();
        }
    }

    @Test
    void bfsCanBeCancelledMidTraversalFromAnotherThread() throws Exception {
        Graph graph = TestGraphs.randomGraph(300_000, 6, 3L);
        int fullSize;
        try (ParallelTraverser reference = new ParallelTraverser(8)) {
            fullSize = reference.bfs(graph, 0).visitOrder().size();
        }

        CancellationToken token = new CancellationToken();
        CountDownLatch reachedBudget = new CountDownLatch(1);
        AtomicInteger seen = new AtomicInteger();
        try (ParallelTraverser traverser = new ParallelTraverser(8)) {
            ExecutorService runner = Executors.newSingleThreadExecutor();
            try {
                Future<TraversalResult> future = runner.submit(() ->
                        traverser.bfs(graph, 0, token,
                                node -> {
                                    if (seen.incrementAndGet() == 500) {
                                        reachedBudget.countDown();
                                    }
                                }));

                assertThat(reachedBudget.await(10, TimeUnit.SECONDS)).isTrue();
                token.cancel();

                TraversalResult result = future.get(10, TimeUnit.SECONDS);
                runner.shutdown();
                assertThat(result.isCancelled()).isTrue();
                assertThat(result.visitOrder().size()).isBetween(500, fullSize - 1);
                assertThat(result.visitOrder()).doesNotHaveDuplicates();
                result.levels().forEach(level ->
                        assertThat(level).isSorted().doesNotHaveDuplicates());
                assertThat(result.stats().visitedCount())
                        .isEqualTo(result.visitOrder().size());
                assertTraverserStillUsable(traverser);
            } finally {
                runner.shutdownNow();
            }
        }
    }

    @Test
    void dfsCanBeCancelledMidTraversalFromAnotherThread() throws Exception {
        Graph graph = TestGraphs.randomGraph(300_000, 6, 4L);
        int fullSize;
        try (ParallelTraverser reference = new ParallelTraverser(8)) {
            fullSize = reference.dfs(graph, 0).visitOrder().size();
        }

        CancellationToken token = new CancellationToken();
        CountDownLatch reachedBudget = new CountDownLatch(1);
        AtomicInteger seen = new AtomicInteger();
        try (ParallelTraverser traverser = new ParallelTraverser(8)) {
            ExecutorService runner = Executors.newSingleThreadExecutor();
            try {
                Future<TraversalResult> future = runner.submit(() ->
                        traverser.dfs(graph, 0, token,
                                node -> {
                                    if (seen.incrementAndGet() == 500) {
                                        reachedBudget.countDown();
                                    }
                                }));

                assertThat(reachedBudget.await(10, TimeUnit.SECONDS)).isTrue();
                token.cancel();

                TraversalResult result = future.get(10, TimeUnit.SECONDS);
                runner.shutdown();
                assertThat(result.isCancelled()).isTrue();
                assertThat(result.visitOrder().size()).isBetween(500, fullSize - 1);
                assertThat(result.visitOrder()).doesNotHaveDuplicates();
                assertThat(result.stats().visitedCount())
                        .isEqualTo(result.visitOrder().size());
                assertTraverserStillUsable(traverser);
            } finally {
                runner.shutdownNow();
            }
        }
    }

    private static void assertTraverserStillUsable(ParallelTraverser traverser) {
        // Worker threads must have left the cancelled run and the pool must
        // still serve subsequent traversals to completion.
        List<Integer> chainOrder = traverser.dfs(TestGraphs.chain(100), 0).visitOrder();
        assertThat(chainOrder).hasSize(100).doesNotHaveDuplicates();
    }
}
