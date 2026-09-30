package com.example.gsb.graph;

import java.util.Arrays;

/** Minimal growable int array; doubles as a LIFO stack. Not thread-safe. */
final class IntBag {

    private int[] data = new int[16];
    private int size;

    void add(int value) {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = value;
    }

    void push(int value) {
        add(value);
    }

    int pop() {
        return data[--size];
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void clear() {
        size = 0;
    }

    int[] toArray() {
        return Arrays.copyOf(data, size);
    }

    int copyInto(int[] target, int position) {
        System.arraycopy(data, 0, target, position, size);
        return position + size;
    }
}
