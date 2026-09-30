package com.example.gsb.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Parallel graph traversal component supporting BFS and DFS with configurable
 * parallelism, atomic visit de-duplication, deterministic visit orders and
 * cooperative cancellation.
 *
 * <h2>De-duplication</h2>
 * Each node carries one slot in an {@link AtomicIntegerArray}. A node becomes
 * visited through a single successful {@code compareAndSet(0, 1)};
 * losing workers never emit the node. Cycles and self-loops therefore cannot
 * cause duplicate visits or non-termination.
 *
 * <h2>Determinism</h2>
 * Output order is a function of the graph and the parallelism only, never of
 * thread scheduling:
 *
 * <ul>
 *   <li><b>BFS</b> is level-synchronous. The current frontier is split into
 *       contiguous index chunks (worker {@code i} always gets chunk
 *       {@code i}), all workers expand one round and synchronize, and the next
 *       frontier is merged in worker order and then sorted by node id. BFS
 *       depth is a graph-intrinsic property, so every level's node set is
 *       scheduling-independent; sorting fixes the within-level order. BFS
 *       output is identical at every parallelism (including 1).</li>
 *   <li><b>DFS</b> statically assigns every node to an owner worker via
 *       {@code node % parallelism}; only the owner may mark or enqueue its
 *       nodes. Within a round each worker runs an ordinary LIFO traversal of
 *       its own stack (neighbors are pushed in reverse sorted order, so the
 *       smallest id pops first) and routes foreign-owned discoveries to that
 *       owner's bucket. After a barrier, each bucket is sorted, de-duplicated
 *       and pushed in descending order. The emitted sequence is the merge by
 *       (round, worker id). Timing changes only <em>when</em> a worker runs,
 *       never the contents of its stack, so repeated runs are byte-for-byte
 *       identical. At parallelism 1 the output equals a canonical sequential
 *       recursive-style DFS preorder exactly.</li>
 * </ul>
 *
 * <h2>Cancellation</h2>
 * Workers inspect the {@link CancellationToken} at every node. On
 * cancellation they stop expanding and finish their current round, so all
 * pool threads return promptly; the partial BFS levels / DFS prefix already
 * produced remain available in the returned {@link TraversalResult}.
 *
 * <p>Instances hold a fixed thread pool and are reusable; call
 * {@link #close()} when done.
 */
public final class ParallelTraverser implements AutoCloseable {

    private final int parallelism;
    private final ExecutorService pool;

    public ParallelTraverser(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1, got " + parallelism);
        }
        this.parallelism = parallelism;
        AtomicInteger index = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable, "graph-traverser-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    public int parallelism() {
        return parallelism;
    }

    /** Runs a parallel BFS that cannot be cancelled. */
    public TraversalResult bfs(Graph graph, int source) {
        return bfs(graph, source, new CancellationToken());
    }

    /**
     * Parallel level-synchronous BFS. The result contains exactly the fully
     * discovered levels at the cancellation point; a level still being
     * discovered when {@code token} is cancelled is omitted.
     */
    public TraversalResult bfs(Graph graph, int source, CancellationToken token) {
        return bfs(graph, source, token, null);
    }

    /**
     * Parallel BFS with a visit listener invoked once per emitted node (in
     * visit order, on the coordinating thread). The listener must be
     * non-blocking; it may cancel {@code token}, e.g. to stop the traversal
     * after a budget of nodes. Listener exceptions abort the traversal.
     */
    public TraversalResult bfs(Graph graph, int source, CancellationToken token,
                               IntConsumer visitListener) {
        checkSource(graph, source);
        long start = System.nanoTime();
        AtomicIntegerArray visited = new AtomicIntegerArray(graph.nodeCount());
        long[] processed = new long[parallelism];
        List<List<Integer>> levels = new ArrayList<>();
        List<Integer> visitOrder = new ArrayList<>();

        visited.set(source, 1);
        int[] frontier = {source};

        while (frontier.length > 0 && !token.isCancelled()) {
            List<Integer> level = new ArrayList<>(frontier.length);
            for (int node : frontier) {
                level.add(node);
                if (visitListener != null) {
                    visitListener.accept(node);
                }
            }
            levels.add(level);
            visitOrder.addAll(level);

            final int[] current = frontier;
            List<Callable<IntBag>> tasks = new ArrayList<>(parallelism);
            for (int worker = 0; worker < parallelism; worker++) {
                final int workerId = worker;
                final int from = (int) ((long) current.length * worker / parallelism);
                final int to = (int) ((long) current.length * (worker + 1) / parallelism);
                tasks.add(() -> {
                    IntBag bag = new IntBag();
                    for (int i = from; i < to; i++) {
                        if (token.isCancelled()) {
                            break;
                        }
                        int node = current[i];
                        processed[workerId]++;
                        int degree = graph.degree(node);
                        for (int j = 0; j < degree; j++) {
                            int neighbor = graph.neighbor(node, j);
                            if (visited.compareAndSet(neighbor, 0, 1)) {
                                bag.add(neighbor);
                            }
                        }
                    }
                    return bag;
                });
            }
            List<IntBag> bags = runRound(tasks, token);
            if (bags == null) {
                break;
            }

            int total = 0;
            for (IntBag bag : bags) {
                total += bag.size();
            }
            int[] next = new int[total];
            int position = 0;
            for (IntBag bag : bags) {
                position = bag.copyInto(next, position);
            }
            Arrays.sort(next);
            frontier = next;
        }

        long elapsed = System.nanoTime() - start;
        TraversalStats stats = new TraversalStats(graph.nodeCount(), graph.edgeCount(),
                visitOrder.size(), elapsed, processed, token.isCancelled());
        return new TraversalResult(visitOrder, levels, stats);
    }

    /** Runs a parallel DFS that cannot be cancelled. */
    public TraversalResult dfs(Graph graph, int source) {
        return dfs(graph, source, new CancellationToken());
    }

    /**
     * Parallel owner-partitioned, round-synchronized DFS. The returned visit
     * order is the deterministic merge of per-worker sequences by
     * (round, worker id); at parallelism 1 it is the canonical sequential DFS
     * preorder. On cancellation the nodes processed so far are returned.
     */
    public TraversalResult dfs(Graph graph, int source, CancellationToken token) {
        return dfs(graph, source, token, null);
    }

    /**
     * Parallel DFS with a visit listener invoked once per processed node.
     * The listener is called concurrently from worker threads, so it must be
     * thread-safe and non-blocking; it may cancel {@code token}. Listener
     * exceptions abort the traversal.
     */
    public TraversalResult dfs(Graph graph, int source, CancellationToken token,
                               IntConsumer visitListener) {
        checkSource(graph, source);
        long start = System.nanoTime();
        AtomicIntegerArray visited = new AtomicIntegerArray(graph.nodeCount());
        long[] processed = new long[parallelism];

        IntBag[] stacks = new IntBag[parallelism];
        Bucket[] buckets = new Bucket[parallelism];
        for (int i = 0; i < parallelism; i++) {
            stacks[i] = new IntBag();
            buckets[i] = new Bucket();
        }

        List<Integer> visitOrder = new ArrayList<>();
        List<List<Integer>> levels = new ArrayList<>();

        visited.set(source, 1);
        stacks[source % parallelism].push(source);

        while (true) {
            List<Callable<List<Integer>>> tasks = new ArrayList<>(parallelism);
            for (int worker = 0; worker < parallelism; worker++) {
                final int workerId = worker;
                tasks.add(() -> {
                    List<Integer> sequence = new ArrayList<>();
                    IntBag stack = stacks[workerId];
                    while (!stack.isEmpty() && !token.isCancelled()) {
                        int node = stack.pop();
                        sequence.add(node);
                        processed[workerId]++;
                        if (visitListener != null) {
                            visitListener.accept(node);
                        }
                        int degree = graph.degree(node);
                        // Reverse sorted order: smallest unvisited neighbor is
                        // pushed last and therefore popped first.
                        for (int j = degree - 1; j >= 0; j--) {
                            int neighbor = graph.neighbor(node, j);
                            if (neighbor % parallelism == workerId) {
                                if (visited.compareAndSet(neighbor, 0, 1)) {
                                    stack.push(neighbor);
                                }
                            } else if (visited.get(neighbor) == 0) {
                                buckets[neighbor % parallelism].add(neighbor);
                            }
                        }
                    }
                    return sequence;
                });
            }
            List<List<Integer>> roundSequences = runRound(tasks, token);
            if (roundSequences == null) {
                break;
            }
            for (List<Integer> sequence : roundSequences) {
                visitOrder.addAll(sequence);
            }
            if (token.isCancelled()) {
                break;
            }

            boolean workRemaining = false;
            for (int worker = 0; worker < parallelism; worker++) {
                int[] discovered = buckets[worker].drainToArray();
                if (discovered.length > 0) {
                    Arrays.sort(discovered);
                    // Traverse descending so duplicates are adjacent and the
                    // smallest surviving id ends up on the stack top.
                    int previous = -1;
                    for (int i = discovered.length - 1; i >= 0; i--) {
                        int node = discovered[i];
                        if (node == previous) {
                            continue;
                        }
                        previous = node;
                        if (visited.compareAndSet(node, 0, 1)) {
                            stacks[worker].push(node);
                        }
                    }
                }
                if (!stacks[worker].isEmpty()) {
                    workRemaining = true;
                }
            }
            if (!workRemaining) {
                break;
            }
        }

        long elapsed = System.nanoTime() - start;
        TraversalStats stats = new TraversalStats(graph.nodeCount(), graph.edgeCount(),
                visitOrder.size(), elapsed, processed, token.isCancelled());
        return new TraversalResult(visitOrder, levels, stats);
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }

    private static void checkSource(Graph graph, int source) {
        if (source < 0 || source >= graph.nodeCount()) {
            throw new IllegalArgumentException(
                    "Source " + source + " out of range [0, " + graph.nodeCount() + ")");
        }
    }

    private <T> List<T> runRound(List<Callable<T>> tasks, CancellationToken token) {
        try {
            List<Future<T>> futures = pool.invokeAll(tasks);
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            token.cancel();
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("Traversal worker failed", cause);
        }
    }

    /** Per-owner bucket filled concurrently by other workers. */
    private static final class Bucket {

        private final IntBag bag = new IntBag();

        synchronized void add(int node) {
            bag.add(node);
        }

        synchronized int[] drainToArray() {
            int[] array = bag.toArray();
            bag.clear();
            return array;
        }
    }
}
