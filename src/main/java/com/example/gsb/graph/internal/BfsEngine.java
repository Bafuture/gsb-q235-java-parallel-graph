package com.example.gsb.graph.internal;

import com.example.gsb.graph.Graph;
import com.example.gsb.graph.TraversalKind;
import com.example.gsb.graph.TraversalResult;
import com.example.gsb.graph.TraversalStats;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 并行 BFS（层同步 bulk-synchronous）。
 *
 * <p>每一层：把当前 frontier 切成小块并行展开；新节点用 VarHandle CAS 原子认领，
 * 每个新节点恰好一个线程认领成功（去重、只 visit 一次）。一层全部结束后，
 * 把本轮新发现的节点按 id 升序排序再作为下一层 frontier——顺序只由图结构决定，
 * 与线程调度无关，因此并行结果与"每层升序的单线程 BFS"逐节点一致。
 *
 * <p>层很小（不足以均分给各线程）时直接在编排线程内联执行，避免同步开销；
 * 这也保证链式图不会产生百万次无意义的屏障。
 */
public final class BfsEngine {

    private static final VarHandle STATE = MethodHandles.arrayElementVarHandle(int[].class);
    private static final int UNSEEN = 0;
    private static final int VISITED = 1;

    private final Graph graph;
    private final int start;
    private final int parallelism;
    private final CancellationToken token;

    private final int n;
    private final int[] seen;
    private final ConcurrentHashMap<String, AtomicLong> perThread = new ConcurrentHashMap<>();
    private final AtomicLong visitedTotal = new AtomicLong();
    public BfsEngine(Graph graph, int start, int parallelism, CancellationToken token) {
        this.graph = graph;
        this.start = start;
        this.parallelism = parallelism;
        this.token = token;
        this.n = graph.vertexCount();
        this.seen = new int[n];
    }

    private boolean cancelled() {
        return token.isCancelled();
    }

    public TraversalResult run() {
        long t0 = System.nanoTime();

        List<List<Integer>> levels = new ArrayList<>();
        List<Integer> order = new ArrayList<>();

        List<Integer> frontier = new ArrayList<>();
        if (claim(start)) {
            frontier.add(start);
            order.add(start);
        }

        boolean poolCreated = parallelism > 1;
        ExecutorService pool = poolCreated
                ? Executors.newFixedThreadPool(parallelism - 1, r -> {
                    Thread t = new Thread(r, "pgt-bfs-worker");
                    t.setDaemon(true);
                    return t;
                })
                : null;

        try {
            while (!frontier.isEmpty() && !cancelled()) {
                levels.add(List.copyOf(frontier));
                List<Integer> next = new ArrayList<>();

                if (pool != null && frontier.size() >= Math.max(2, parallelism)) {
                    expandParallel(pool, frontier, next);
                } else {
                    expandRange(frontier, 0, frontier.size(), next);
                }

                if (cancelled()) {
                    break;
                }

                next.sort(Integer::compareTo);
                order.addAll(next);
                frontier = next;
            }
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
        }

        long elapsed = System.nanoTime() - t0;
        TraversalStats stats = buildStats(elapsed);
        return new TraversalResult(TraversalKind.BFS, order, levels, token.isCancelled(), stats);
    }

    private void expandParallel(ExecutorService pool, List<Integer> frontier, List<Integer> next) {
        int chunkSize = 1024;
        int taskCount = (frontier.size() + chunkSize - 1) / chunkSize;
        // 每个任务独占一个 bucket，避免并发写同一个 ArrayList。
        List<List<Integer>> buckets = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            buckets.add(new ArrayList<>());
        }
        List<Future<?>> futures = new ArrayList<>(taskCount);
        for (int t = 0; t < taskCount; t++) {
            final int from = t * chunkSize;
            final int to = Math.min(from + chunkSize, frontier.size());
            final List<Integer> bucket = buckets.get(t);
            futures.add(pool.submit(() -> {
                if (!cancelled()) {
                    expandRange(frontier, from, to, bucket);
                }
            }));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException("BFS expansion failed", e);
            }
        }
        for (List<Integer> bucket : buckets) {
            next.addAll(bucket);
        }
    }

    private void expandRange(List<Integer> frontier, int from, int to, List<Integer> out) {
        for (int i = from; i < to && !cancelled(); i++) {
            int u = frontier.get(i);
            for (int v : graph.neighbors(u)) {
                if (claim(v)) {
                    out.add(v);
                }
            }
        }
    }

    private boolean claim(int v) {
        if (STATE.compareAndSet(seen, v, UNSEEN, VISITED)) {
            visitedTotal.incrementAndGet();
            perThread.computeIfAbsent(Thread.currentThread().getName(), k -> new AtomicLong()).incrementAndGet();
            return true;
        }
        return false;
    }

    private TraversalStats buildStats(long elapsedNanos) {
        Map<String, Long> map = new TreeMap<>();
        perThread.forEach((name, counter) -> map.put(name, counter.get()));
        return new TraversalStats(n, graph.edgeCount(), visitedTotal.get(), elapsedNanos, map);
    }
}
