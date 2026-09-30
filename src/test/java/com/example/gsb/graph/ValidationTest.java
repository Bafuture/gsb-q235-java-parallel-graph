package com.example.gsb.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationTest {

    @Test
    void rejectsInvalidParallelism() {
        Graph g = Graphs.chain(3);
        assertThatThrownBy(() -> ParallelGraphTraverser.bfs(g, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParallelGraphTraverser.dfs(g, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeVertices() {
        Graph g = Graphs.chain(3);
        assertThatThrownBy(() -> ParallelGraphTraverser.bfs(g, 3, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Graph.builder(3).addEdge(0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disconnectedVerticesAreNotVisited() {
        Graph.Builder b = Graph.builder(5);
        b.addEdge(0, 1).addEdge(1, 2);
        Graph g = b.build();
        TraversalResult r = ParallelGraphTraverser.bfs(g, 0, 3);
        assertThat(r.order()).containsExactly(0, 1, 2);
        assertThat(r.stats().visitedCount()).isEqualTo(3);
    }
}
