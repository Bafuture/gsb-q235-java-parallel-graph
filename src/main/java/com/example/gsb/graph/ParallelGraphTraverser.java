package com.example.gsb.graph;

import com.example.gsb.graph.internal.BfsEngine;
import com.example.gsb.graph.internal.CancellationToken;
import com.example.gsb.graph.internal.DfsEngine;

/**
 * 并行图遍历入口。
 *
 * <p>确定性保证：输出顺序只依赖图结构与并行度无关的规范规则，与线程调度无关——
 * BFS 按层推进、层内按顶点 id 升序；DFS 输出规范先序（等价于邻接按 id 升序的单线程迭代 DFS）。
 * 因此同一图、同一并行度（事实上任意并行度）多次运行结果完全一致，且与单线程结果一致。
 */
public final class ParallelGraphTraverser {

    private ParallelGraphTraverser() {
    }

    /** 同步执行并行 BFS。 */
    public static TraversalResult bfs(Graph graph, int start, int parallelism) {
        return run(graph, start, parallelism, TraversalKind.BFS).result();
    }

    /** 同步执行并行 DFS。 */
    public static TraversalResult dfs(Graph graph, int start, int parallelism) {
        return run(graph, start, parallelism, TraversalKind.DFS).result();
    }

    /**
     * 异步启动一次遍历，返回句柄。可通过句柄取消；取消后已处理结果仍可用。
     */
    public static TraversalHandle run(Graph graph, int start, int parallelism, TraversalKind kind) {
        validate(graph, start, parallelism);
        TraversalHandle handle = new TraversalHandle();
        CancellationToken token = new CancellationToken();
        handle.setCancelAction(token::cancel);
        Thread orchestrator = new Thread(() -> {
            TraversalResult result = kind == TraversalKind.BFS
                    ? new BfsEngine(graph, start, parallelism, token).run()
                    : new DfsEngine(graph, start, parallelism, token).run();
            handle.complete(result);
        }, "pgt-orchestrator");
        orchestrator.setDaemon(true);
        orchestrator.start();
        return handle;
    }

    private static void validate(Graph graph, int start, int parallelism) {
        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1, got " + parallelism);
        }
        if (start < 0 || start >= graph.vertexCount()) {
            throw new IllegalArgumentException("start vertex " + start + " out of range [0," + graph.vertexCount() + ")");
        }
    }
}
