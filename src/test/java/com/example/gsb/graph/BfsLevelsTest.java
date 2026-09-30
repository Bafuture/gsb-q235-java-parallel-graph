package com.example.gsb.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BfsLevelsTest {

    @Test
    void levelsMatchReferenceAndAreSortedWithinLevel() {
        Graph g = Graphs.cyclicDense(120, 42L);
        TraversalResult r = ParallelGraphTraverser.bfs(g, 0, 4);
        List<List<Integer>> expected = ReferenceTraversal.bfsLevels(g, 0);
        assertThat(r.levels()).isEqualTo(expected);

        for (List<Integer> level : r.levels()) {
            assertThat(level).isSorted();
            assertThat(level).doesNotHaveDuplicates();
        }

        List<Integer> flattened = r.levels().stream().flatMap(List::stream).collect(java.util.stream.Collectors.toList());
        assertThat(flattened).isEqualTo(r.order());
    }

    @Test
    void gridLevelsAreSortedAndDeterministic() {
        Graph g = Graphs.grid(8, 8);
        TraversalResult first = ParallelGraphTraverser.bfs(g, 0, 4);
        for (int i = 0; i < 3; i++) {
            assertThat(ParallelGraphTraverser.bfs(g, 0, 4).levels()).isEqualTo(first.levels());
        }
        assertThat(first.levels().get(2)).containsExactly(2, 9, 16);
    }

    @Test
    void dfsHasNoLevels() {
        TraversalResult dfs = ParallelGraphTraverser.dfs(Graphs.grid(3, 3), 0, 2);
        assertThat(dfs.levels()).isNull();
    }
}
