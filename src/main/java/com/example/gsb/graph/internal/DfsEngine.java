package com.example.gsb.graph.internal;

import com.example.gsb.graph.Graph;
import com.example.gsb.graph.TraversalKind;
import com.example.gsb.graph.TraversalResult;
import com.example.gsb.graph.TraversalStats;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 并行 DFS = 并行 LIFO 探索 + 确定性顺序归约。
 *
 * <p><b>并行探索（带来加速的部分）</b>：多个 worker 从共享 LIFO 栈抢占节点（深度优先偏向），
 * 每个节点通过 VarHandle CAS 原子标记，恰好被一个 worker 认领并展开一次，
 * 因此同一节点在并发下只被访问一次，环与自环不会导致重复或死循环；总工作量 O(V+E)，必然终止。
 * worker 之间用 active 计数 + Condition 的经典空闲/终止协议收敛。
 *
 * <p><b>确定性（与调度无关的部分）</b>：探索只负责"可达性 + 去重"，不决定输出位置。
 * 收敛（或取消）后，以已发现节点集为限，按邻接 id 升序做一次迭代先序 DFS 归约。
 * 该归约是图结构的纯函数：同一图多次运行、不同并行度（含单线程）输出完全一致，
 * 且与单线程迭代 DFS 逐节点一致。取消时已发现集合沿发现树对根闭包，归约仍得到有效部分结果。
 */
public final class DfsEngine {

    private static final VarHandle SEEN = MethodHandles.arrayElementVarHandle(int[].class);

    private final Graph graph;
    private final int start;
    private final int parallelism;
    private final CancellationToken token;

    private final int n;
    private final int[] seen;
    private final ConcurrentLinkedDeque<Integer> stack = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, AtomicLong> perThread = new ConcurrentHashMap<>();
    private final AtomicLong visitedTotal = new AtomicLong();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();
    private int activeWorkers;
    private boolean terminated;

    public DfsEngine(Graph graph, int start, int parallelism, CancellationToken token) {
        this.graph = graph;
        this.start = start;
        this.parallelism = parallelism;
        this.token = token;
        this.n = graph.vertexCount();
        this.seen = new int[n];
        this.activeWorkers = parallelism;
    }

    public TraversalResult run() {
        long t0 = System.nanoTime();
        stack.addLast(start);

        List<Thread> workers = new ArrayList<>(parallelism);
        for (int i = 0; i < parallelism; i++) {
            Thread w = new Thread(this::workerLoop, "pgt-dfs-worker-" + i);
            w.setDaemon(true);
            workers.add(w);
            w.start();
        }
        for (Thread w : workers) {
            try {
                w.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<Integer> order = canonicalPreorder();
        long elapsed = System.nanoTime() - t0;
        TraversalStats stats = buildStats(elapsed);
        return new TraversalResult(TraversalKind.DFS, order, null, token.isCancelled(), stats);
    }

    private void workerLoop() {
        while (true) {
            Integer u = stack.pollLast();
            if (u != null) {
                if (claim(u)) {
                    process(u);
                }
                continue;
            }
            // 栈空：进入空闲状态。全部 worker 空闲且栈仍空时收敛终止。
            lock.lock();
            try {
                if (!stack.isEmpty()) {
                    continue;
                }
                if (!terminated) {
                    activeWorkers--;
                    while (!terminated && stack.isEmpty() && !token.isCancelled()) {
                        if (activeWorkers == 0) {
                            terminated = true;
                            cond.signalAll();
                            return;
                        }
                        cond.awaitNanos(50_000L);
                        if (token.isCancelled()) {
                            terminated = true;
                            cond.signalAll();
                            return;
                        }
                    }
                    if (terminated || token.isCancelled()) {
                        return;
                    }
                    activeWorkers++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }
        }
    }

    private boolean claim(int u) {
        return SEEN.compareAndSet(seen, u, 0, 1);
    }

    private void process(int u) {
        visitedTotal.incrementAndGet();
        perThread.computeIfAbsent(Thread.currentThread().getName(), k -> new AtomicLong()).incrementAndGet();

        int[] neighbors = graph.neighbors(u);
        int pushed = 0;
        // 反向压栈使 LIFO 下 id 最小的邻接边最先被弹出（深度优先偏向）。
        for (int i = neighbors.length - 1; i >= 0; i--) {
            int v = neighbors[i];
            if (token.isCancelled()) {
                return;
            }
            if ((int) SEEN.getAcquire(seen, v) != 0) {
                continue;
            }
            stack.addLast(v);
            pushed++;
        }
        if (pushed > 0) {
            // 仅在确有空闲 worker 时才进锁唤醒，避免每个邻居一次锁的开销。
            lock.lock();
            try {
                if (activeWorkers < parallelism) {
                    cond.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 确定性归约：在已发现节点集上，按邻接 id 升序做迭代先序 DFS。
     * 图结构的纯函数，与线程调度、并行度无关。
     */
    private List<Integer> canonicalPreorder() {
        boolean[] emitted = new boolean[n];
        long cap = Math.min(visitedTotal.get(), (long) Integer.MAX_VALUE);
        List<Integer> order = new ArrayList<>((int) cap);
        Deque<Integer> dfs = new ArrayDeque<>();
        if ((int) SEEN.getAcquire(seen, start) != 0) {
            dfs.push(start);
        }
        while (!dfs.isEmpty()) {
            int u = dfs.pop();
            if (emitted[u]) {
                continue;
            }
            emitted[u] = true;
            order.add(u);
            int[] neighbors = graph.neighbors(u);
            for (int i = neighbors.length - 1; i >= 0; i--) {
                int v = neighbors[i];
                if (!emitted[v] && (int) SEEN.getAcquire(seen, v) != 0) {
                    dfs.push(v);
                }
            }
        }
        return order;
    }

    private TraversalStats buildStats(long elapsedNanos) {
        Map<String, Long> map = new TreeMap<>();
        perThread.forEach((name, counter) -> map.put(name, counter.get()));
        return new TraversalStats(n, graph.edgeCount(), visitedTotal.get(), elapsedNanos, map);
    }
}
