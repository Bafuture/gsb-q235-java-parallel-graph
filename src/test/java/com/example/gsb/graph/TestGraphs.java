package com.example.gsb.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic test-graph factories and sequential reference traversals. */
final class TestGraphs {

    private TestGraphs() {
    }

    /** Pseudo-random directed graph, fully determined by {@code seed}. */
    static Graph randomGraph(int nodes, int edgesPerNode, long seed) {
        Random random = new Random(seed);
        Graph.Builder builder = Graph.builder(nodes);
        for (int u = 0; u < nodes; u++) {
            for (int k = 0; k < edgesPerNode; k++) {
                builder.addEdge(u, random.nextInt(nodes));
            }
        }
        return builder.build();
    }

    /** 0 -> 1 -> ... -> n-1 */
    static Graph chain(int n) {
        Graph.Builder builder = Graph.builder(n);
        for (int i = 0; i + 1 < n; i++) {
            builder.addEdge(i, i + 1);
        }
        return builder.build();
    }

    /** Chain plus a back edge n-1 -> 0. */
    static Graph cycle(int n) {
        Graph.Builder builder = Graph.builder(n);
        for (int i = 0; i < n; i++) {
            builder.addEdge(i, (i + 1) % n);
        }
        return builder.build();
    }

    /**
     * Two dense layers: the source points to every node of layer A, and every
     * node of layer A points to every node of layer B. Maximises the number of
     * concurrent discoverers racing on the same nodes.
     */
    static Graph denseBipartite(int layerSize) {
        Graph.Builder builder = Graph.builder(1 + 2 * layerSize);
        for (int a = 0; a < layerSize; a++) {
            builder.addEdge(0, 1 + a);
        }
        for (int a = 0; a < layerSize; a++) {
            for (int b = 0; b < layerSize; b++) {
                builder.addEdge(1 + a, 1 + layerSize + b);
            }
        }
        return builder.build();
    }

    /** Sequential BFS reference: levels as sorted lists. */
    static List<List<Integer>> sequentialBfsLevels(Graph graph, int source) {
        boolean[] visited = new boolean[graph.nodeCount()];
        List<List<Integer>> levels = new ArrayList<>();
        visited[source] = true;
        List<Integer> frontier = List.of(source);
        while (!frontier.isEmpty()) {
            levels.add(frontier);
            Set<Integer> next = new TreeSet<>();
            for (int u : frontier) {
                for (int i = 0; i < graph.degree(u); i++) {
                    int v = graph.neighbor(u, i);
                    if (!visited[v]) {
                        visited[v] = true;
                        next.add(v);
                    }
                }
            }
            frontier = new ArrayList<>(next);
        }
        return levels;
    }

    /** Sequential iterative DFS preorder reference (smallest id first). */
    static List<Integer> sequentialDfsPreorder(Graph graph, int source) {
        boolean[] visited = new boolean[graph.nodeCount()];
        List<Integer> order = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        visited[source] = true;
        stack.push(source);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            order.add(u);
            for (int i = graph.degree(u) - 1; i >= 0; i--) {
                int v = graph.neighbor(u, i);
                if (!visited[v]) {
                    visited[v] = true;
                    stack.push(v);
                }
            }
        }
        return order;
    }

    static Set<Integer> reachableSet(Graph graph, int source) {
        Set<Integer> reachable = new HashSet<>();
        for (List<Integer> level : sequentialBfsLevels(graph, source)) {
            reachable.addAll(level);
        }
        return reachable;
    }
}
