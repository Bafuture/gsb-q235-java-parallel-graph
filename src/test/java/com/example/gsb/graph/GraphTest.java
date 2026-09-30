package com.example.gsb.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GraphTest {

    @Test
    void sortsNeighborsAndMergesDuplicateEdges() {
        Graph graph = Graph.builder(4)
                .addEdge(0, 3).addEdge(0, 1).addEdge(0, 2)
                .addEdge(0, 1).addEdge(0, 3)
                .build();

        assertThat(graph.degree(0)).isEqualTo(3);
        assertThat(graph.neighbor(0, 0)).isEqualTo(1);
        assertThat(graph.neighbor(0, 1)).isEqualTo(2);
        assertThat(graph.neighbor(0, 2)).isEqualTo(3);
        assertThat(graph.edgeCount()).isEqualTo(3);
    }

    @Test
    void keepsSelfLoopsInAdjacencyAndEdgeCount() {
        Graph graph = Graph.builder(2)
                .addEdge(0, 0)
                .addEdge(0, 1)
                .build();

        assertThat(graph.degree(0)).isEqualTo(2);
        assertThat(graph.neighbor(0, 0)).isEqualTo(0);
        assertThat(graph.neighbor(0, 1)).isEqualTo(1);
        assertThat(graph.edgeCount()).isEqualTo(2);
    }

    @Test
    void supportsNodesWithoutEdgesAndEmptyGraph() {
        Graph graph = Graph.builder(3).addEdge(0, 1).build();
        assertThat(graph.nodeCount()).isEqualTo(3);
        assertThat(graph.degree(2)).isZero();

        Graph empty = Graph.builder(0).build();
        assertThat(empty.nodeCount()).isZero();
        assertThat(empty.edgeCount()).isZero();
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> Graph.builder(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Graph.builder(2).addEdge(0, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Graph.builder(2).addEdge(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);

        Graph graph = Graph.builder(2).addEdge(0, 1).build();
        assertThatThrownBy(() -> graph.degree(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graph.neighbor(0, 5))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void buildsAdjacencyFromEdgeArray() {
        Graph graph = Graph.fromEdges(3, new int[][]{{0, 1}, {0, 2}, {1, 2}, {1, 2}});
        assertThat(graph.edgeCount()).isEqualTo(3);
        assertThat(graph.degree(1)).isEqualTo(1);
    }
}
