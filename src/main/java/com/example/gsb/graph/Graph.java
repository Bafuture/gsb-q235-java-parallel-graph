package com.example.gsb.graph;

import java.util.Arrays;

/**
 * Immutable directed graph backed by a memory-efficient CSR
 * (compressed sparse row) representation.
 *
 * <p>Node ids are dense integers in {@code [0, nodeCount)}. Adjacency lists
 * are sorted by node id at build time and duplicate edges are merged, so the
 * traversal algorithms can rely on a canonical neighbor order. Self-loops
 * (edges of the form {@code (v, v)}) are retained: they count toward the edge
 * total but are harmless to traversal because the node is already marked when
 * the self-loop is examined.
 */
public final class Graph {

    private final int nodeCount;
    private final int[] offsets;
    private final int[] edges;

    private Graph(int nodeCount, int[] offsets, int[] edges) {
        this.nodeCount = nodeCount;
        this.offsets = offsets;
        this.edges = edges;
    }

    /** Creates a builder for a graph with {@code nodeCount} nodes. */
    public static Builder builder(int nodeCount) {
        return new Builder(nodeCount);
    }

    /**
     * Builds a graph from an array of {@code [from, to]} pairs. Duplicate
     * pairs are merged.
     */
    public static Graph fromEdges(int nodeCount, int[][] edges) {
        Builder b = builder(nodeCount);
        for (int[] edge : edges) {
            if (edge.length != 2) {
                throw new IllegalArgumentException("Each edge must be a [from, to] pair");
            }
            b.addEdge(edge[0], edge[1]);
        }
        return b.build();
    }

    public int nodeCount() {
        return nodeCount;
    }

    /** Number of unique directed edges after build-time de-duplication. */
    public long edgeCount() {
        return offsets[nodeCount];
    }

    public int degree(int node) {
        checkNode(node);
        return offsets[node + 1] - offsets[node];
    }

    /** Returns the {@code index}-th neighbor of {@code node} (ascending id). */
    public int neighbor(int node, int index) {
        checkNode(node);
        if (index < 0 || index >= degree(node)) {
            throw new IndexOutOfBoundsException(index);
        }
        return edges[offsets[node] + index];
    }

    private void checkNode(int node) {
        if (node < 0 || node >= nodeCount) {
            throw new IllegalArgumentException(
                    "Node " + node + " out of range [0, " + nodeCount + ")");
        }
    }

    @Override
    public String toString() {
        return "Graph{nodes=" + nodeCount + ", edges=" + edgeCount() + "}";
    }

    /** Incremental builder for {@link Graph}. Not thread-safe. */
    public static final class Builder {

        private final int nodeCount;
        private int[] from;
        private int[] to;
        private int edgeCount;

        private Builder(int nodeCount) {
            if (nodeCount < 0) {
                throw new IllegalArgumentException("nodeCount must be >= 0");
            }
            this.nodeCount = nodeCount;
            this.from = new int[16];
            this.to = new int[16];
        }

        public Builder addEdge(int u, int v) {
            if (u < 0 || u >= nodeCount || v < 0 || v >= nodeCount) {
                throw new IllegalArgumentException(
                        "Edge endpoint out of range: (" + u + ", " + v + ")");
            }
            ensureCapacity(edgeCount + 1);
            from[edgeCount] = u;
            to[edgeCount] = v;
            edgeCount++;
            return this;
        }

        public Graph build() {
            int[] offsets = new int[nodeCount + 1];
            for (int i = 0; i < edgeCount; i++) {
                offsets[from[i] + 1]++;
            }
            for (int i = 1; i <= nodeCount; i++) {
                offsets[i] += offsets[i - 1];
            }

            int[] adjacency = new int[edgeCount];
            int[] cursor = Arrays.copyOf(offsets, nodeCount);
            for (int i = 0; i < edgeCount; i++) {
                adjacency[cursor[from[i]]++] = to[i];
            }

            // Sort each adjacency segment, then compact duplicates into a
            // new CSR using one global write pointer.
            for (int u = 0; u < nodeCount; u++) {
                Arrays.sort(adjacency, offsets[u], offsets[u + 1]);
            }
            int[] compacted = new int[edgeCount];
            int[] compactedOffsets = new int[nodeCount + 1];
            int write = 0;
            for (int u = 0; u < nodeCount; u++) {
                int start = offsets[u];
                int end = offsets[u + 1];
                int previous = Integer.MIN_VALUE;
                for (int i = start; i < end; i++) {
                    int v = adjacency[i];
                    if (v != previous) {
                        compacted[write++] = v;
                        previous = v;
                    }
                }
                compactedOffsets[u + 1] = write;
            }
            int uniqueEdges = compactedOffsets[nodeCount];
            compacted = Arrays.copyOf(compacted, uniqueEdges);

            return new Graph(nodeCount, compactedOffsets, compacted);
        }

        private void ensureCapacity(int required) {
            if (required <= from.length) {
                return;
            }
            int newCapacity = Math.max(required, from.length * 2);
            from = Arrays.copyOf(from, newCapacity);
            to = Arrays.copyOf(to, newCapacity);
        }
    }
}
