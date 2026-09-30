package com.example.gsb.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 单线程规范参照遍历。 */
final class ReferenceTraversal {

    private ReferenceTraversal() {
    }

    /** 迭代式先序 DFS：邻接按 id 升序（Graph 已保证），用栈反向压入。 */
    static List<Integer> dfs(Graph g, int start) {
        boolean[] seen = new boolean[g.vertexCount()];
        List<Integer> order = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            if (seen[u]) {
                continue;
            }
            seen[u] = true;
            order.add(u);
            int[] nb = g.neighbors(u);
            for (int i = nb.length - 1; i >= 0; i--) {
                int v = nb[i];
                if (!seen[v]) {
                    stack.push(v);
                }
            }
        }
        return order;
    }

    /** BFS：每层完成后按 id 升序排序再展开下一层。 */
    static List<Integer> bfs(Graph g, int start) {
        boolean[] seen = new boolean[g.vertexCount()];
        seen[start] = true;
        List<Integer> order = new ArrayList<>();
        List<Integer> frontier = new ArrayList<>();
        frontier.add(start);
        while (!frontier.isEmpty()) {
            order.addAll(frontier);
            Set<Integer> next = new LinkedHashSet<>();
            for (int u : frontier) {
                for (int v : g.neighbors(u)) {
                    if (!seen[v]) {
                        seen[v] = true;
                        next.add(v);
                    }
                }
            }
            List<Integer> sorted = new ArrayList<>(next);
            sorted.sort(Integer::compareTo);
            frontier = sorted;
        }
        return order;
    }

    static List<List<Integer>> bfsLevels(Graph g, int start) {
        boolean[] seen = new boolean[g.vertexCount()];
        seen[start] = true;
        List<List<Integer>> levels = new ArrayList<>();
        List<Integer> frontier = new ArrayList<>();
        frontier.add(start);
        while (!frontier.isEmpty()) {
            levels.add(List.copyOf(frontier));
            Set<Integer> next = new LinkedHashSet<>();
            for (int u : frontier) {
                for (int v : g.neighbors(u)) {
                    if (!seen[v]) {
                        seen[v] = true;
                        next.add(v);
                    }
                }
            }
            List<Integer> sorted = new ArrayList<>(next);
            sorted.sort(Integer::compareTo);
            frontier = sorted;
        }
        return levels;
    }
}
