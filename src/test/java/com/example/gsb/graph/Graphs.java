package com.example.gsb.graph;

import java.util.Random;

/** 测试用图生成器。 */
final class Graphs {

    private Graphs() {
    }

    /** 随机有向图（可能含环、自环、重复边）。 */
    static Graph random(int vertices, int edges, long seed) {
        Random rnd = new Random(seed);
        Graph.Builder b = Graph.builder(vertices);
        for (int i = 0; i < edges; i++) {
            b.addEdge(rnd.nextInt(vertices), rnd.nextInt(vertices));
        }
        return b.build();
    }

    /** 带环的稠密小图。 */
    static Graph cyclicDense(int vertices, long seed) {
        Random rnd = new Random(seed);
        Graph.Builder b = Graph.builder(vertices);
        for (int u = 0; u < vertices; u++) {
            for (int k = 0; k < 4; k++) {
                b.addEdge(u, rnd.nextInt(vertices));
            }
        }
        return b.build();
    }

    /** 网格图：rows x cols，每个节点向右、向下连边（无环、高并行度）。 */
    static Graph grid(int rows, int cols) {
        Graph.Builder b = Graph.builder(rows * cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int id = r * cols + c;
                if (c + 1 < cols) {
                    b.addEdge(id, id + 1);
                }
                if (r + 1 < rows) {
                    b.addEdge(id, id + cols);
                }
            }
        }
        return b.build();
    }

    /** 链式图 0->1->2->...->n-1。 */
    static Graph chain(int n) {
        Graph.Builder b = Graph.builder(n);
        for (int i = 0; i + 1 < n; i++) {
            b.addEdge(i, i + 1);
        }
        return b.build();
    }

    /** 带自环与重复边的图。 */
    static Graph withSelfLoopsAndDuplicates() {
        Graph.Builder b = Graph.builder(6);
        b.addEdge(0, 0);          // 自环
        b.addEdge(0, 1);
        b.addEdge(0, 1);          // 重复边
        b.addEdge(1, 2);
        b.addEdge(2, 2);          // 自环
        b.addEdge(2, 0);          // 回边成环
        b.addEdge(1, 3);
        b.addEdge(3, 4);
        b.addEdge(4, 1);          // 环
        b.addEdge(4, 5);
        return b.build();
    }
}
