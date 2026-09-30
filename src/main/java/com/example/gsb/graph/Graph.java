package com.example.gsb.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 不可变有向图。顶点为稠密整数 id [0, vertexCount)。
 * 邻接表在构建时按目标顶点 id 升序排序并去重，为遍历提供规范（canonical）顺序。
 * 自环与重复边被保留输入但去重后存储，不会导致重复遍历。
 */
public final class Graph {

    private final int vertexCount;
    private final int[][] adjacency;
    private final long edgeCount;

    private Graph(int vertexCount, int[][] adjacency, long edgeCount) {
        this.vertexCount = vertexCount;
        this.adjacency = adjacency;
        this.edgeCount = edgeCount;
    }

    public static Builder builder(int vertexCount) {
        return new Builder(vertexCount);
    }

    public int vertexCount() {
        return vertexCount;
    }

    public long edgeCount() {
        return edgeCount;
    }

    /** 顶点 v 的出边目标，按 id 升序、已去重。 */
    public int[] neighbors(int v) {
        return adjacency[v];
    }

    public int outDegree(int v) {
        return adjacency[v].length;
    }

    public static final class Builder {
        private final int vertexCount;
        private final List<int[]> edges = new ArrayList<>();

        private Builder(int vertexCount) {
            if (vertexCount < 0) {
                throw new IllegalArgumentException("vertexCount must be >= 0");
            }
            this.vertexCount = vertexCount;
        }

        public Builder addEdge(int from, int to) {
            checkVertex(from);
            checkVertex(to);
            edges.add(new int[]{from, to});
            return this;
        }

        private void checkVertex(int v) {
            if (v < 0 || v >= vertexCount) {
                throw new IllegalArgumentException("vertex " + v + " out of range [0," + vertexCount + ")");
            }
        }

        public Graph build() {
            Map<Integer, List<Integer>> buckets = new HashMap<>();
            for (int[] e : edges) {
                buckets.computeIfAbsent(e[0], k -> new ArrayList<>()).add(e[1]);
            }
            int[][] adjacency = new int[vertexCount][];
            long edgeCount = 0;
            for (int v = 0; v < vertexCount; v++) {
                List<Integer> list = buckets.get(v);
                if (list == null) {
                    adjacency[v] = new int[0];
                } else {
                    int[] arr = list.stream().mapToInt(Integer::intValue).distinct().sorted().toArray();
                    adjacency[v] = arr;
                    edgeCount += arr.length;
                }
            }
            return new Graph(vertexCount, adjacency, edgeCount);
        }
    }

    @Override
    public String toString() {
        return "Graph{vertices=" + vertexCount + ", edges=" + edgeCount + ", adjacency=" + Arrays.deepToString(adjacency) + "}";
    }
}
